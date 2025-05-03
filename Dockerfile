FROM openjdk:21-jdk-slim as base


RUN apt-get update && apt-get install -y python3 python3-pip python3-venv && apt-get clean
WORKDIR /app

RUN python3 -m venv /opt/venv
ENV PATH="/opt/venv/bin:$PATH"
RUN pip install --upgrade pip

RUN pip install grpcio
RUN pip install grpcio-tools
RUN pip install sentence-transformers
RUN pip install faiss-cpu
RUN pip install numpy
WORKDIR /app

COPY target/shard-search-api-1.0-SNAPSHOT.jar /app/my-java-app.jar

COPY faiss/multi_faiss_servers.py /app/multi_faiss_server.py
COPY faiss/faiss_search_api_pb2.py /app/faiss_search_api_pb2.py
COPY faiss/faiss_search_api_pb2_grpc.py /app/faiss_search_api_pb2_grpc.py


VOLUME /data/lucene
VOLUME /data/faiss

EXPOSE 9090
EXPOSE 9092

CMD ["sh", "-c", "\
   python3 multi_faiss_server.py /data/faiss 50000 & \
   java -jar my-java-app.jar"]
