# lettuce-out-of-order

This project demonstrates a bug in the [Lettuce](https://github.com/redis/lettuce) Java Redis client, where under certain circumstances the client enters a state where it mixes up the responses from concurrent Redis operations with one another.
