# lettuce-out-of-order

This project demonstrates a bug in the [Lettuce](https://github.com/redis/lettuce) Java Redis client, where under certain circumstances the client enters a state where it mixes up the responses from concurrent Redis operations with one another.

## How to start
In the command line run one of the tests 
* `mvn -Dtest=SpringDataExampleTest#errorSimulation test`
* `mvn -Dtest=PureLettuceSetupTest#errorSimulation test`

## The logic behind the trigger of the bug
1. The test will start concurrent redis `set` and `get` in a multi-threads mode. 
2. A jvm fatal error is thrown randomly during the `set` operation.
3. The return value of the `get` operation will be polluted with wrong values from other `get` operations. 
4. The test will fail since the number of value mismatching errors should be 0.

