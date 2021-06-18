# OpenJML-Automatic-Counterexample-Generation
The code in this GitHub project implements the idea in the NIER paper.

## Project Directory
You can find all relevant sources in `src/main/java`. Specifically, `src/main/java/openjml` contains 
the entry function for this tool and the code for the whole unit test generation pipeline.
1. `Main`: the entry class and the implementation of the whole pipeline
2. `JMLTransformer`: used to add relevant sections to code for counterexample extraction
3. `MethodAggregator`: used to search for method body via method signature

The interesting code is in the `Main` function, where the actual implementation is provided.

## Code Explanation
The `Main` class is split into two sections:
1. Construct Test Suite: this section creates the skeleton of the test suite with the driver methods of all methods
2. Construct Counterexample: this section uses OpenJML to determine the correct counterexample, and 
generates a test for each counterexample.

To leverage parsing, we are using OpenJML`s API to parse Java code. Executing or ESC and RAC are performed
through processes where we invoke the OpenJML executable.

## Building and Running
The project was build on `OpenJDK 8` using the Gradle build system. To run this project, use the following command:

`./gradlew run --args="<your_java_file_here"`.
