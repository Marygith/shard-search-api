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

    def getSimilarityScores(self, request, context):
        try:
            print("Received SimilarityRequest")

            query = request.query
            ids_to_vector = request.id_to_vector

            query_embedding = self.model.encode([query])[0].astype(np.float32)

            scores = {}

            for doc_id, vector_message in ids_to_vector.items():
                try:
                    passage_embedding = np.array(vector_message.values, dtype=np.float32)

                    if passage_embedding.shape != (384,):
                        raise ValueError(
                            f"Invalid vector shape for doc_id {doc_id}, vector shape is {passage_embedding.shape}")

                    l2_distance = np.sum((query_embedding - passage_embedding) ** 2)
                    scores[doc_id] = l2_distance

                except Exception as e:
                    print(f"Error processing doc_id {doc_id}: {e}")
                    scores[doc_id] = float('inf')

            print("Returning SimilarityResponse: ", scores)

            return faiss_search_api_pb2.SimilarityResponse(scores=scores)

        except Exception as e:
            print("Error in getSimilarityScores:", e)
            context.set_code(grpc.StatusCode.INTERNAL)
            context.set_details(str(e))
            return faiss_search_api_pb2.SimilarityResponse()

def serve_faiss(index_path, port):
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
            raise ValueError("SHARD_IDS environment variable is not set")

        shard_ids = [int(s.strip()) for s in shard_ids_env.split(",")]
        print(f"Initializing shards: {shard_ids}")
#         index_files = [f for f in os.listdir(index_folder) if f.startswith('faiss_index_with_ids')]
        threads = []
        print("list dir " + index_folder)
        print(os.listdir(index_folder))
        print("list dir /data:")
        print(os.listdir("/data"))
#         print("list dir /app/data:")
#         print(os.listdir("/app/data"))
        print("list dir /data/faiss:")
        print(os.listdir("/data/faiss"))
        print("list dir /app:")
        print(os.listdir("/app"))
#         print(f"about to start, index files: {index_files}")

        for shard_id in shard_ids:
            index_file = f"faiss_index_with_ids-{shard_id}"
            full_path = os.path.join(index_folder, index_file)
            if not os.path.exists(full_path):
                print(f"[WARNING] Index file not found: {full_path}")
                conti
            port = ports_start + shard_id
            print(f"Starting server on port {port} for shard {shard_id} from file {full_path}")
            t = threading.Thread(target=serve_faiss, args=(full_path, port))
            t.start()
            threads.append(t)

            for t in threads:
                t.join()
    except Exception as e:
        print(f"Exception during FAISS server startup: {e}")
        import traceback
        traceback.print_exc()

#         for index_file in index_files:
#             try:
#                 shard_id = int(index_file.split("-")[-1])
#                 port = ports_start + shard_id
#                 full_path = os.path.join(index_folder, index_file)
#                 print(f"starting server on port {port} for shard {shard_id} from file {full_path}")
#                 t = threading.Thread(target=serve_faiss_with_exception_handling, args=(full_path, port))
#                 t.start()
#                 threads.append(t)
#             except Exception as e:
#                 print(f"[❌ ERROR] Failed to start thread for index file: {index_file}")
#                 traceback.print_exc()
#
#         for t in threads:
#             t.join()
#     except Exception as e:
#         print("[❌ FATAL ERROR] Failed during init phase")
#         traceback.print_exc()