CS416 Project 3 – Dynamic Routing (Distance Vector)
1. Project Overview
   This project implements a dynamic routing protocol for a virtual network.
   Unlike Project 2, routers no longer use hardcoded forwarding tables.
   Instead, each router automatically computes routes using the Distance Vector (DV) algorithm.
   Key features:
   1)Dynamic route learning
   2)Automatic next-hop selection
   3)Subnet-based routing
   4)No flooding at the router level

2. Network Configuration
   The network is defined in config.txt.
   The topology includes:
   3 host subnets: net1, net2, net3
   7 transit subnets connecting routers
   Total: 10 subnets
   Each device is connected via switches and routers according to the assignment topology.

3. Routing Protocol (Distance Vector)
   Each router maintains:
   distanceVector: subnet → cost
   nextHop: subnet → next router
   Algorithm:
   1.Initialize:
   Directly connected subnets → cost = 0
   2.Periodically send DV to neighbors
   3.Upon receiving DV:
   Apply Bellman-Ford:
   newCost = neighborCost + 1
   4.Update routing table if shorter path is found

4. Packet Types
   Two types of packets are used:
   (1) DATA packets
   DATA|srcMac:dstMac:srcIP:dstIP:message
   Used for user communication.

(2) DV packets
DV|routerID|subnet=cost,...
Used for routing updates between routers.

5. Routing Behavior
   (1)Routers forward packets based on destination subnet
   (2)If destination is directly connected:
   Send to corresponding switch/host
   (3)Otherwise:
   Forward to next-hop router
   The router never floods packets

6. How to Run
   Open multiple terminals (or laptops if required):

Step 1: Compile
javac *.java

Step 2: Start switches
java VirtualSwitch S1
java VirtualSwitch S2
java VirtualSwitch S3

Step 3: Start routers
java VirtualRouterDV R1
java VirtualRouterDV R2
java VirtualRouterDV R3
java VirtualRouterDV R4
java VirtualRouterDV R5
java VirtualRouterDV R6
Step 4: Start hosts
java VirtualHost A
java VirtualHost B
java VirtualHost C

Step 5: Wait for convergence
Wait ~5–10 seconds for DV to stabilize.

Step 6: Send messages
Example:
net2.B hello
net3.C test

7. Demo Scenarios

Test 1: A → B
Expected path:
R1 → R3
Expected:
(1)Only R1 and R3 print forwarding logs
(2)Host B receives message

Test 2: A → C
Expected path:
R1 → R2 → R4 → R6
Test 3: C → B
Possible paths:
R6 → R4 → R5 → R3
OR
R6 → R4 → R2 → R3

8. Notes
   (1)All links have cost = 1
   (2)No failures assumed
   (3)DV updates every 2 seconds
   (4)Routing convergence required before testing
9. Contribution
   All group members(Yuyang Xia, Yuxin Li, Yao Liu, Bining Yang)contributed to design, implementation, and testing.