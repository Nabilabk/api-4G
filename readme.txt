# 📡 4G/5G Network Mapper

An intelligent network coverage monitoring platform for analyzing
and visualizing mobile network coverage in Morocco.

## ✨ Overview

The application processes heterogeneous telecommunications data
and provides an interactive geographical interface for monitoring
network coverage, identifying risk areas, and exploring antenna
information.

## 🚀 Features

- 📍 Interactive network coverage map
- 📊 Telecom data management
- 🔥 Coverage heatmaps
- 🗺️ GeoJSON visualization
- 🔎 Dynamic filtering
- 🔐 JWT authentication
- 👥 Multi-role access control
- ⚠️ Network risk evaluation
- 📤 CSV / JSON data export
- 🗄️ Structured SQLite database

## 👥 User Roles

The application supports three roles:

- **Admin**
- **Operator**
- **Viewer**

Each role has different access permissions.

## 🏗️ Architecture

```text
OpenCelliD Data
      ↓
Data Processing
      ↓
SQLite Database
      ↓
REST API
      ↓
Authentication / Authorization
      ↓
JavaFX Interface
      ↓
Interactive Map
🛠️ Tech Stack
Java
JavaFX
Spark Java
SQLite
JWT
Maven
Leaflet.js
OpenStreetMap
GeoJSON
📊 Risk Analysis

The platform includes a risk evaluation model combining:

Weighted analysis
Technical heuristics
Haversine distance calculation

This allows geographical and technical information to be
combined when evaluating network coverage risks.

🗺️ Visualization

The application provides:

Interactive maps
Coverage heatmaps
Antenna locations
Dynamic filters
GeoJSON-based geographic data
🎯 Objective

The goal is to provide a centralized platform for exploring,
monitoring, and analyzing mobile network coverage data.

👩‍💻 Author

Nabila Boubekr
