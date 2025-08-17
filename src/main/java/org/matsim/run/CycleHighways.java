package org.matsim.run;


import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.Node;
import org.matsim.api.core.v01.network.Link;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.network.io.MatsimNetworkReader;
import org.matsim.core.network.io.NetworkWriter;


public class CycleHighways {
	private static final double averageBikeSpeed = 2.98;

	static class NodeNew {
		String id;
		Boolean selfIntersection;
		double[] point;
	}

	static class LinkNew {
		String id;
		String fromId;
		String toId;
		String originalLinkId;
		Double euclideanDistance;
		List<double[]> line;
	}

	private static LinkNew getNewLink(JsonNode feature) {
		LinkNew l = new LinkNew();
		l.id = feature.get("properties").get("id").asText();
		l.fromId = feature.get("properties").get("fromID").asText();
		l.toId = feature.get("properties").get("toID").asText();
		l.euclideanDistance = feature.get("properties").get("euclideanDistance").asDouble();
		JsonNode coords = feature.get("geometry").get("coordinates");
		l.line = new ArrayList<>();
		for (JsonNode coordPair : coords) {
			l.line.add(new double[] { coordPair.get(0).asDouble(), coordPair.get(1).asDouble()});
		}
		return l;
	}

	/**
	 * This method creates a new link, based on the parameters and returns it.
	 */
	private static Link createLink(Network network, Id<Link> id, Id<Node> fromId, Id<Node> toId,
								   Double freeSpeed, Double lengthLink) {
		Link newLink = network.getFactory().createLink(
			id,
			network.getNodes().get(fromId),
			network.getNodes().get(toId)
		);
		newLink.setAllowedModes(Set.of(TransportMode.bike));
		newLink.setFreespeed(freeSpeed);
		newLink.setCapacity(100000);
		newLink.setLength(lengthLink);
		newLink.setNumberOfLanes(10);

		if (lengthLink < 0.5) {
			System.out.println("Problematic Link: " + id + ", length: " + lengthLink);
		}
		return newLink;
	}

	/**
	 * This link copies a link, which is already on the network to be used as a bike link in the future
	 */
	private static Link copyLink(Network network, Link link) {
		Id<Link> newLinkId = Id.createLinkId("bike_" + link.getId());
		Link newLink = createLink(
			network,
			newLinkId,
			link.getFromNode().getId(),
			link.getToNode().getId(),
			Math.min(link.getFreespeed(), averageBikeSpeed),
			link.getLength()
		);
		newLink.getAttributes().putAttribute("bikeLinkType", "unchanged");
		return newLink;
	}

	/**
	 * Inserts a new link in the network.
	 * In case the new link is a cycle highway, an additional link is added for the opposite direction.
	 */
	private static void insertNewLink(Network network, LinkNew newLink) {
		Id<Node> fromId = Id.createNodeId(newLink.fromId);
		Id<Node> toId = Id.createNodeId(newLink.toId);
		if (newLink.originalLinkId != null) {
			Id<Link> newLinkId = Id.createLinkId(newLink.id);
			Id<Link> originalLinkId = Id.createLinkId(newLink.originalLinkId);
			double freeSpeed = Math.min(network.getLinks().get(originalLinkId).getFreespeed(), averageBikeSpeed) ;
			network.addLink(createLink(network, newLinkId, fromId, toId, freeSpeed, newLink.euclideanDistance));
			network.getLinks().get(newLinkId).getAttributes().putAttribute("bikeLinkType", "split");

		} else {
			double freeSpeed = 6.2;
			Id<Link> newLinkId1 = Id.createLinkId(newLink.id + "_1");
			network.addLink(createLink(network, newLinkId1, fromId, toId, freeSpeed, newLink.euclideanDistance));
			network.getLinks().get(newLinkId1).getAttributes().putAttribute("bikeLinkType", "cycleHighway");
			Id<Link> newLinkId2 = Id.createLinkId(newLink.id + "_2");
			network.addLink(createLink(network, newLinkId2, toId, fromId, freeSpeed, newLink.euclideanDistance));
			network.getLinks().get(newLinkId2).getAttributes().putAttribute("bikeLinkType", "cycleHighway");
		}
	}

	/**
	 * This method loads all new links and nodes, applies it to the current network and saves it.
	 * The new network contains bike links and non bike-links.
	 */
	public static void main(String[] args) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			String basePath = "output/network/python/";
			String nodesString = basePath + "new_matsim_nodes_combined.geojson";
			String linksCycleHighwaysString = basePath + "new_matsim_links_cycle_highways.geojson";
			String linksNetworkIntersectionString = basePath + "new_matsim_links_network_intersections.geojson";
			JsonNode rootNodes = mapper.readTree(Files.newBufferedReader(Paths.get(nodesString)));
			JsonNode rootCycleHighways = mapper.readTree(Files.newBufferedReader(Paths.get(linksCycleHighwaysString)));
			JsonNode rootNetworkIntersection = mapper.readTree(Files.newBufferedReader(Paths.get(linksNetworkIntersectionString)));

			List<NodeNew> nodes = new ArrayList<>();
			List<LinkNew> linksCycleHighway = new ArrayList<>();
			List<LinkNew> linksNetworkIntersection = new ArrayList<>();
			Set<String> toBeRemoved = new HashSet<>();

			for (JsonNode feature : rootNodes.get("features")) {
				NodeNew n = new NodeNew();
				n.id = feature.get("properties").get("id").asText();
				n.selfIntersection = feature.get("properties").get("selfIntersection").asBoolean();
				JsonNode coords = feature.get("geometry").get("coordinates");
				n.point = new double[] {coords.get(0).asDouble(), coords.get(1).asDouble()};
				nodes.add(n);
			}
			for (JsonNode feature : rootCycleHighways.get("features")) {
				linksCycleHighway.add(getNewLink(feature));
			}
			for (JsonNode feature : rootNetworkIntersection.get("features")) {
				LinkNew l = getNewLink(feature);
				l.originalLinkId = feature.get("properties").get("originalLinkID").asText();
				toBeRemoved.add(l.originalLinkId);
				linksNetworkIntersection.add(l);
			}

			Network network = NetworkUtils.createNetwork();
			new MatsimNetworkReader(network).readFile("input/v6.4/berlin-v6.4-network-with-pt.xml");

			for (NodeNew nodeNew : nodes) {
				Node node = network.getFactory().createNode(Id.createNodeId(nodeNew.id), new Coord(nodeNew.point));
				network.addNode(node);
			}
			List<Link> newLinks = new ArrayList<>();
			for (Link link : network.getLinks().values()) {
				Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
				if (allowedModes.contains(TransportMode.bike)) {
					// Remove bike from allowed modes
					allowedModes.remove(TransportMode.bike);
					link.setAllowedModes(allowedModes);
					if (!toBeRemoved.contains(link.getId().toString())) {
						// Insert new bike link, if it should not be removed
						newLinks.add(copyLink(network, link));
					}
				}
			}
			for (Link newLink : newLinks) {
				network.addLink(newLink);
			}
			for (LinkNew linkNew : linksCycleHighway) {
				insertNewLink(network, linkNew);
			}
			for (LinkNew linkNew : linksNetworkIntersection) {
				insertNewLink(network, linkNew);
			}
			new NetworkWriter(network).write("input/v6.4/berlin-v6.4-network-with-pt-and-cycle-highways.xml");

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
