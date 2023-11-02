def calculate_depth(nodes, i, j):
    if i > j or i < 0 or j >= len(nodes):
        return 0

    node = nodes[i][j]
    left_depth = calculate_depth(nodes, i, node - 1)
    right_depth = calculate_depth(nodes, node + 1, j)
    result = max(left_depth, right_depth) + 1
    return result


def square_matrix(x, target):
    n = len(x)
    assert n >= 5 and target < 0
    for line in x:
        assert len(line) == n
        for elem in line:
            assert elem == target

    # ... some smart work ...
    return "Success"