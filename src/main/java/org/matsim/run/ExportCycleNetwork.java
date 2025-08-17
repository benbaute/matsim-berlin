package org.matsim.run;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;

import java.util.HashSet;
import java.util.Set;

public class ExportCycleNetwork {
	public static void main(String[] args) {
		boolean exportCycleHighways = true;
		Network network = NetworkUtils.createNetwork();

		String inputFileName = "input/v6.4/berlin-v6.4-network-with-pt.xml";
		String outputFileName = "output/network/bike-network.xml";
		if (exportCycleHighways) {
			inputFileName = "input/v6.4/berlin-v6.4-network-with-pt-and-cycle-highways.xml";
			outputFileName = "output/network/cycle-highways-bike-network.xml";
		}

		new MatsimNetworkReader(network).readFile(inputFileName);

		ExportCycleNetwork modifier = new ExportCycleNetwork();
		modifier.filterNetwork(network);

		new NetworkWriter(network).write(outputFileName);
	}

	/**
	 * Only save nodes and like relevant for bikes.
	 */
	public void filterNetwork(Network network) {
		Set<Id<Node>> bikeIds = new HashSet<>();
		for (Link link : network.getLinks().values()) {
			if (!link.getAllowedModes().contains(TransportMode.bike)) {
				network.removeLink(link.getId());
			} else {
				bikeIds.add(link.getToNode().getId());
				bikeIds.add(link.getFromNode().getId());
			}

		}
		for (Node node : network.getNodes().values()) {
			if (!bikeIds.contains(node.getId())) {
				network.removeNode(node.getId());
			}
		}
	}
}
