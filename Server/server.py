from typing import Any, Callable, Dict, List, Optional, Tuple

import flwr as fl
import numpy as np

CLIENTS_REQ = 2


def fit_config(rnd: int):
    """Return training configuration dict for each round.

	Keep batch size fixed at 32, perform two rounds of training with one
	local epoch, increase to two local epochs afterwards.
	"""
    config = {
        "batch_size": 32,
        "local_epochs": 1,
    }

    return config


class SaveModelStrategy(fl.server.strategy.FedAvgAndroid):
    def aggregate_fit(
        self, rnd: int, results, failures,
    ) -> Optional[fl.common.Weights]:
        weights = super().aggregate_fit(rnd, results, failures)
        if weights is not None:

            np.savez(f"round-{rnd}-weights.npz", *weights)

        return weights


save_model_strategy = SaveModelStrategy(
    fraction_fit=1,
    fraction_eval=1,
    min_fit_clients=CLIENTS_REQ,
    min_eval_clients=CLIENTS_REQ,
    min_available_clients=CLIENTS_REQ,
    eval_fn=None,
    on_fit_config_fn=fit_config,
    initial_parameters=None,
)


def main() -> None:
    # Create strategy
    strategy = fl.server.strategy.FedAvgAndroid(
        fraction_fit=1,
        fraction_eval=1,
        min_fit_clients=CLIENTS_REQ,
        min_eval_clients=CLIENTS_REQ,
        min_available_clients=CLIENTS_REQ,
        eval_fn=None,
        on_fit_config_fn=fit_config,
        initial_parameters=None,
    )

    # Start Flower server for 2 rounds of federated learning
    fl.server.start_server(
        "[::]:8999",
        config={"num_rounds": 10},
        strategy=save_model_strategy,
        # force_final_distributed_eval=True,
    )


if __name__ == "__main__":
    main()
