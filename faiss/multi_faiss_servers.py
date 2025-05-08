# multi_faiss_server.py

from concurrent import futures
import grpc
import faiss_search_api_pb2
import faiss_search_api_pb2_grpc

import faiss
import numpy as np
import time
import threading
import sys
import os

class FaissSearchService(faiss_search_api_pb2_grpc.FaissSearchServiceServicer):
    def __init__(self, index_path):
        print(f"Loading FAISS index from {index_path}")
        self.index = faiss.read_index(index_path)

    def search(self, request, context):
        query = request.query
        print("came query: ")
        k = request.k
        query_embedding = np.array(request.query, dtype=np.float32).reshape(1, -1)

        distances, ids = self.index.search(query_embedding, k)
        distances = distances[0]
        ids = ids[0]
        norm_dists = distances

        results = [
            faiss_search_api_pb2.FaissResult(id=int(ids[i]), score=float(norm_dists[i]))
            for i in range(len(ids)) if ids[i] != -1
        ]

        print("returning results from faiss: ")
        return faiss_search_api_pb2.FaissSearchResponse(results=results)

def serve_faiss(index_path, port):
    print("about to serve faiss")
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    faiss_search_api_pb2_grpc.add_FaissSearchServiceServicer_to_server(FaissSearchService(index_path), server)
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
