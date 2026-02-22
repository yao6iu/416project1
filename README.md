# CS416 Project 2 – Implementation of IP Forwarding

This project simulates a small internetwork using UDP sockets.

It includes:
- Virtual Hosts
- Ethernet Learning Switches
- Virtual Routers
- IP-based forwarding across multiple subnets

The system demonstrates how packets travel from one subnet to another through routers.

---

## Network Topology

There are three subnets:

Subnet 1:
A, B → S1 → R1

Subnet 2:
R1 → R2

Subnet 3:
R2 → S2 → C, D

Devices in the system:

Switches:
- S1
- S2

Routers:
- R1
- R2

Hosts:
- A
- B
- C
- D

---

## Frame Format

All frames use the following format:

SRC_MAC:DST_MAC:SRC_IP:DST_IP:MESSAGE

Example:

A:R1:net1.A:net3.D:hello

Fields:

- SRC_MAC → Virtual MAC (device ID)
- DST_MAC → Next-hop MAC
- SRC_IP → Virtual IP
- DST_IP → Destination virtual IP
- MESSAGE → Payload

---

## Forwarding Logic

1. A host sends a frame to its default gateway.
2. The switch forwards the frame using MAC learning.
3. The router checks the destination subnet.
4. The router rewrites MAC addresses.
5. The frame is forwarded to the next hop.
6. The destination host receives and prints the message.

---

## Router Forwarding Tables

R1:
- net1 → DIRECT
- net2 → DIRECT
- net3 → via net2.R2

R2:
- net3 → DIRECT
- net2 → DIRECT
- net1 → via net2.R1

---

## How to Run

Start each device separately using its ID.

Switches:
VirtualSwitch S1
VirtualSwitch S2

Routers:
VirtualRouter R1
VirtualRouter R2

Hosts:
VirtualHost A
VirtualHost B
VirtualHost C
VirtualHost D

Make sure switches and routers are started before hosts.

---

## Features Implemented

- Ethernet learning switch (Layer 2)
- IP forwarding (Layer 3)
- MAC address rewriting at routers
- Multi-hop routing
- Cross-subnet communication