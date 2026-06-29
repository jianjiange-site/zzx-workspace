"""AI Chat Service entry point."""
import grpc
from concurrent import futures


def serve():
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=10))
    # TODO: register services
    server.add_insecure_port("[::]:50051")
    print("AI Chat Service starting on :50051...")
    server.start()
    server.wait_for_termination()


if __name__ == "__main__":
    serve()
