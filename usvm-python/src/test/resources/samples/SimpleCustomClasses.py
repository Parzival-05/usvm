class ClassWithMatmulAndAdd:
    def __init__(self):
        pass

    def __matmul__(self, other):
        return self

    def __add__(self, other):
        return self

    def __bool__(self):
        return True


class ClassWithMatmulAndSub:
    def __init__(self):
        pass

    def __matmul__(self, other):
        return self

    def __sub__(self, other):
        return self

    def __bool__(self):
        return False


def matmul_usage(x):
    return x @ x


def matmul_and_add(x):
    y = x + 10
    return y @ x


def matmul_add_and_sub(x):
    if x:
        return (x + 10) @ x
    else:
        return (x - 10) @ x


def iterable_of_matmul(x):
    y = ClassWithMatmulAndAdd()
    for elem in x:
        y += elem @ 1
    assert len(x) >= 3


class ClassWithField:
    def __init__(self, value):
        self.field = value


def use_int_field(obj):
    assert obj.field == 123456