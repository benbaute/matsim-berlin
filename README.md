# Cycle Highways in Berlin

This project analyzes the planned [cycle highways](https://www.berlin.de/sen/uvk/mobilitaet-und-verkehr/verkehrsplanung/radverkehr/radschnellverbindungen/) in Berlin.
To accomplish that it adds the new cycle highways on top of the available network.

## Overview

This repository contains the JAVA-code of this project.
The Python-code can be found [here](https://github.com/benbaute/matsim-berlin-analysis).

## Creating a network file with cycle highways

The step-by-step guide can be found in the [Python-repository](https://github.com/benbaute/matsim-berlin-analysis).

## Simulation

The file [Berlin Scenario](src/main/java/org/matsim/run/OpenBerlinScenario.java) executes the scenario.
It requires the adjusted network file with the cycle highways.
This file can be [downloaded](https://tubcloud.tu-berlin.de/s/2FkQE9FsDWRmP5s) or created by following
the directions in the [Python-repository](https://github.com/benbaute/matsim-berlin-analysis).
In the file [Berlin Scenario](src/main/java/org/matsim/run/OpenBerlinScenario.java) the number of iterations, the chosen plans file, 
and the speed on cycle highways can be adjusted accordingly for a new simulation. 
