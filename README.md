# CS416 Project 2 – Implementation of IP Forwarding

This project simulates a small internetwork using UDP sockets.

It includes:
- Virtual Hosts
- Ethernet Learning Switches
- Virtual Routers
- IP-based packet forwarding across multiple subnets

The system demonstrates how packets are forwarded across routers between different subnets.

---

## Network Topology

There are three subnets:

Subnet 1:
A, B -> S1 -> R1

Subnet 2:
R1 -> S2 -> R2

Subnet 3:
R2 -> S3 -> C, D

Routers:
- R1 connects net1 and net2
- R2 connects net2 and net3

---

## Frame Format

Each frame contains five fields:

SRC_MAC : DST_MAC : SRC_IP : DST_IP : MESSAGE

Example:
A:R1:net1.A:net3.D:hello

---

## How It Works

1. A host sends a frame to its default gateway.
2. The switch forwards the frame based on MAC learning.
3. The router checks the destination subnet.
4. The router rewrites the MAC address.
5. The frame is forwarded to the next hop.
6. The destination host receives and prints the message.

---

## How to Run

Start each device separately with its ID as argument.

Examples:

VirtualSwitch S1
VirtualSwitch S2
VirtualSwitch S3

VirtualRouter R1
VirtualRouter R2

VirtualHost A
VirtualHost B
VirtualHost C
VirtualHost D

Make sure switches and routers are started before hosts.

---

## Features Implemented

- Layer 2 learning switch
- Layer 3 IP forwarding
- Router forwarding table
- MAC address rewriting
- Multi-hop routing across subnets