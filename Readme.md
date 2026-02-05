README

This project implements a simple Ethernet learning switch system for CS416 Project 1.

Project Structure
src/
├── VirtualSwitch.java     // Virtual Ethernet switch implementation
├── VirtualHost.java       // Virtual host implementation
├── ConfigParser.java      // Parses configuration file
├── DeviceInfo.java        // Stores device information (ID, IP, port)
└── config.txt             // Network topology configuration
README.md

Files included:
Java source files in the src directory
Configuration file used to describe switches, hosts, and links

How to run:
1. Open the project in IntelliJ IDEA.
2. Make sure a JDK (Java 17 or higher) is configured.
3. Create Run Configurations for Switches
   Create three Application configurations:
   Name: Switch S1
   Main Class: VirtualSwitch
   Program Arguments: S1
   Name: Switch S2
   Main Class: VirtualSwitch
   Program Arguments: S2
   Name: Switch S3
   Main Class: VirtualSwitch
   Program Arguments: S3

4. Create Run Configurations for Hosts
   Create four Application configurations:
   Name: Host A
   Main Class: VirtualHost
   Program Arguments: A
   Name: Host B
   Main Class: VirtualHost
   Program Arguments: B
   Name: Host C
   Main Class: VirtualHost
   Program Arguments: C
   Name: Host D
   Main Class: VirtualHost
   Program Arguments: D

5. Start the Programs,Run the programs in the following order:
   Start all switches
   Switch S1,Switch S2,Switch S3
   Start all hosts
   Host A,Host B,Host C,Host D

6.Testing
In the Host A console, enter: D hello
Expected behavior:
Switches S1, S2, and S3 learn MAC address A and print their switch tables
Host D prints:
Received message from A: hello
Hosts B and C print a MAC address mismatch debug message
Host A does not receive the message back
This demonstrates correct flooding and MAC learning behavior.

Each device will bind to its configured IP address and UDP port.

The configuration file must be present in the project directory so the program can read the network topology at runtime.
To simulate multiple switches, run multiple instances of the program in IntelliJ, each with a different switch ID.

Group members:
Yuyang Xia
Yuxin Li
Yao Liu
Bining Yang