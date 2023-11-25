class A:
    def __init__(self, left: 'B', right: 'B'):
        self.left = left
        self.right = right


class B:
    def __init__(self, data):
        self.data_list = data


class C:
    def __init__(self, pos):
        self.pos = pos


def f(x):
    if x.left.data_list and x.right.data_list:
        # print("left data_list:", x.left.data_list, flush=True)
        # print("right data_list:", x.right.data_list, flush=True)
        a = x.left.data_list.pop(0)
        b = x.right.data_list.pop(0)
        if a + b == 155:
            return 1
        return 2
    return 3


def g(x):
    while x.left.data_list or x.right.data_list:
        print("left data_list:", x.left.data_list, flush=True)
        print("right data_list:", x.right.data_list, flush=True)
        a = x.left.data_list.pop(0)
        b = x.right.data_list.pop(0)
        if a.pos + b.pos == 155:
            return 1
    return 2
