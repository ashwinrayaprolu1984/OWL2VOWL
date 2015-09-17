package de.uni_stuttgart.vis.vowl.owl2vowl.model.nodes;

import org.semanticweb.owlapi.model.IRI;

import java.util.Set;

public interface HasIntersections {
	void addElementToIntersection(IRI iri);

	Set getElementOfIntersection();
}
