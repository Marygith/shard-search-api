# multi_faiss_server.py

from concurrent import futures
import grpc
import faiss_search_api_pb2
import faiss_search_api_pb2_grpc

from sentence_transformers import SentenceTransformer
import faiss
import numpy as np
import time
import threading
import sys
import os

print("start of python app")
# ======== GLOBAL MODEL LOADING ===========
# Load the model ONCE globally, before any threads are started
shared_model = SentenceTransformer('all-MiniLM-L6-v2')
print("[MODEL] Loaded SentenceTransformer model once globally")
# ==========================================

class FaissSearchService(faiss_search_api_pb2_grpc.FaissSearchServiceServicer):
    def __init__(self, index_path, model):
        print("about to use shared model")
        self.model = model  # Use the shared model!
        print(f"Loading FAISS index from {index_path}")
        self.index = faiss.read_index(index_path)

    def search(self, request, context):
        query = request.query
        k = request.k
        normalized = request.normalized

        query_embedding = self.model.encode([query], convert_to_tensor=False)
        query_embedding = query_embedding.astype('float32')

        distances, ids = self.index.search(query_embedding, k)
        distances = distances[0]
        ids = ids[0]

        if normalized:
            max_distance = max(distances)
            min_distance = min(distances)
            norm_dists = [
                1 - (d - min_distance) / (max_distance - min_distance) if max_distance > min_distance else 1.0
                for d in distances
            ]
        else:
            norm_dists = distances

        results = [
            faiss_search_api_pb2.FaissResult(id=int(ids[i]), score=float(norm_dists[i]))
            for i in range(len(ids)) if ids[i] != -1
        ]

        return faiss_search_api_pb2.FaissSearchResponse(results=results)

    def encodeQuery(self, request, context):
        try:
            print("Received EncodeRequest")

            query = request.query
            query_embedding = self.model.encode([query])[0].astype(np.float32)

            return faiss_search_api_pb2.EncodedQuery(
                vector=faiss_search_api_pb2.Vector(values=query_embedding.tolist())
            )
        except Exception as e:
            print("Error encoding query:", e)
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            return faiss_search_api_pb2.EncodedQuery()

def serve_faiss(index_path, port):
    print("about to serve faiss")
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    # Pass the shared model into FaissSearchService
    faiss_search_api_pb2_grpc.add_FaissSearchServiceServicer_to_server(FaissSearchService(index_path, shared_model), server)
    server.add_insecure_port(f'[::]:{port}')
    print(f"[FAISS SERVER] Serving index {index_path} on port {port}")
    server.start()
    try:
        while True:
            time.sleep(86400)
    except KeyboardInterrupt:
        server.stop(0)

def serve_faiss_with_exception_handling(index_path, port):
    try:
        serve_faiss(index_path, port)
    except Exception as e:
        print(f"[❌ ERROR] Failed to start FAISS server for index '{index_path}' on port {port}")
        traceback.print_exc()

if __name__ == '__main__':
    print("Starting python init")
    try:
        index_folder = sys.argv[1]  # Example: /data/faiss
        ports_start = int(sys.argv[2])  # Example: 50000

        print(f"Starting servers for all indexes in {index_folder}, starting at port {ports_start}")

        shard_ids_env = os.getenv("FAISS_SHARD_IDS")
        if not shard_ids_env:
            print("shard ids are not set, exiting")
            sys.exit(0)

        shard_ids = [int(s.strip()) for s in shard_ids_env.split(",")]
        print(f"Initializing shards: {shard_ids}")
#         index_files = [f for f in os.listdir(index_folder) if f.startswith('faiss_index_with_ids')]
        threads = []

        for shard_id in shard_ids:
            index_file = f"faiss_index_with_ids-{shard_id}"
            full_path = os.path.join(index_folder, index_file)
            if not os.path.exists(full_path):
                print(f"[WARNING] Index file not found: {full_path}")
                continue
            port = ports_start + shard_id
            print(f"Starting server on port {port} for shard {shard_id} from file {full_path}")
            t = threading.Thread(target=serve_faiss_with_exception_handling, args=(full_path, port))
            t.start()
            threads.append(t)

        for t in threads:
            t.join()
    except Exception as e:
        print(f"Exception during FAISS server startup: {e}")
        import traceback
        traceback.print_exc()
