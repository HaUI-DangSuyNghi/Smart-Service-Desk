"""Service Desk binding for generic executable invariant evidence."""

from hdpi.evaluation.security import evaluate as evaluate_generic

from .dataset import load_dataset


def evaluate(root, dataset_root, *, junit, checks):
    return evaluate_generic(load_dataset(dataset_root, root), junit=junit, checks=checks)
