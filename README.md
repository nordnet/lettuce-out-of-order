# lettuce-out-of-order

This project demonstrates a bug in the [Lettuce](https://github.com/redis/lettuce) Java Redis client, where under certain circumstances the client enters a state where it mixes up the responses from concurrent Redis operations with one another.

## The logic behind the trigger of the bug
1. Run either PureLettuceSetupTest.java or PureLettuceSetupTest.java
2. The test will start concurrent redis `set` and `get`.
3. A jvm fatal error is thrown randomly during the `set` operation.
4. The return value of the `get` operation will be polluted with wrong values from other `get` operations. 
5. The test will fail since the number of value mismatching errors should be 0.

