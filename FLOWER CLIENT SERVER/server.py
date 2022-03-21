
import flwr as fl

fl.server.start_server(config={"num_rounds": 3}, force_final_distributed_eval=True,)
