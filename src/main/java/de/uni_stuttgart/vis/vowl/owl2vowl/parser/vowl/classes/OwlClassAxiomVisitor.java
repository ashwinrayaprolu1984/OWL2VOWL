package de.uni_stuttgart.vis.vowl.owl2vowl.parser.vowl.classes;

import de.uni_stuttgart.vis.vowl.owl2vowl.constants.PropertyAllSomeValue;
import de.uni_stuttgart.vis.vowl.owl2vowl.constants.VowlAttribute;
import de.uni_stuttgart.vis.vowl.owl2vowl.model.data.VowlData;
import de.uni_stuttgart.vis.vowl.owl2vowl.model.entities.nodes.AbstractNode;
import de.uni_stuttgart.vis.vowl.owl2vowl.model.entities.nodes.classes.AbstractClass;
import de.uni_stuttgart.vis.vowl.owl2vowl.model.entities.properties.AbstractProperty;
import de.uni_stuttgart.vis.vowl.owl2vowl.model.entities.properties.DatatypeValueReference;
import de.uni_stuttgart.vis.vowl.owl2vowl.model.entities.properties.ObjectValueReference;
import de.uni_stuttgart.vis.vowl.owl2vowl.parser.owlapi.IndividualsVisitor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.semanticweb.owlapi.model.*;

import java.util.Set;

public class OwlClassAxiomVisitor implements OWLObjectVisitor {

	private VowlData vowlData;
	private OWLClass owlClass;
	private Logger logger = LogManager.getLogger(OwlClassAxiomVisitor.class);

	public OwlClassAxiomVisitor(VowlData vowlData, OWLClass owlClass) {
		this.vowlData = vowlData;
		this.owlClass = owlClass;
	}

	@Override
	public void doDefault(Object object) {
		logger.info("Unsupported axiom: " + object);
	}

	@Override
	public void visit(OWLEquivalentClassesAxiom axiom) {
		// TODO NamedClasses size != 1 means either
		if (axiom.getNamedClasses().size() != 1) {
			createEquivalentClass(axiom);
			return;
		}

		OWLClass referencedClass = axiom.getNamedClasses().iterator().next();

		Set<OWLClassExpression> expressionsWithoutRefClass = axiom.getClassExpressionsMinus(referencedClass);
		for (OWLClassExpression anonymExpressions : expressionsWithoutRefClass) {
			anonymExpressions.accept(new OwlClassAxiomVisitor(vowlData, referencedClass));
		}
	}

	private void createEquivalentClass(OWLEquivalentClassesAxiom axiom) {
		AbstractClass topClass = vowlData.getClassForIri(owlClass.getIRI());

		for (OWLClassExpression owlClassExpression : axiom.getClassExpressionsMinus(owlClass)) {
			topClass.addEquivalentElement(owlClassExpression.asOWLClass().getIRI());
		}

		topClass.addAttribute(VowlAttribute.EQUIVALENT);
	}

	@Override
	public void visit(OWLSubClassOfAxiom axiom) {
		if (axiom.isGCI()) {
			// TODO anonym subclass behaviour
			logger.info("Anonym subclass: " + axiom);
			return;
		}

		OWLClass subClass = axiom.getSubClass().asOWLClass();
		AbstractClass vowlSubclass = vowlData.getClassForIri(subClass.getIRI());

		if (axiom.getSuperClass().isAnonymous()) {
			axiom.getSuperClass().accept(new OwlClassAxiomVisitor(vowlData, owlClass));
		} else {
			OWLClass superClass = axiom.getSuperClass().asOWLClass();
			AbstractClass vowlSuperClass = vowlData.getClassForIri(superClass.getIRI());
			vowlSubclass.addSuperEntity(vowlSuperClass.getIri());
			vowlSuperClass.addSubEntity(vowlSubclass.getIri());
		}
	}

	@Override
	public void visit(OWLDisjointClassesAxiom axiom) {
		for (OWLDisjointClassesAxiom pairwiseAxiom : axiom.asPairwiseAxioms()) {
			IRI[] domainRange = new IRI[2];
			int index = 0;

			for (OWLClass aClass : pairwiseAxiom.getClassesInSignature()) {
				domainRange[index++] = aClass.getIRI();
			}

			if (!vowlData.getSearcher().containsDisjoint(domainRange[0], domainRange[1])) {
				vowlData.getGenerator().generateDisjointProperty(domainRange[0], domainRange[1]);
			}
		}
	}

	// TODO equivalent expressions not processed
	@Override
	public void visit(OWLDisjointUnionAxiom axiom) {
		if (axiom.getOWLClass().isAnonymous()) {
			logger.info("Disjoint Union base is anonym.");
			return;
		}

		AbstractClass baseClass = vowlData.getClassForIri(axiom.getOWLClass().getIRI());
		baseClass.addAttribute(VowlAttribute.DISJOINTUNION);

		for (OWLClass disjointClass : axiom.getOWLDisjointClassesAxiom().getClassesInSignature()) {
			baseClass.addDisjointUnion(disjointClass.getIRI());
		}
	}

	@Override
	public void visit(OWLObjectMinCardinality ce) {
		if (!ce.getFiller().isOWLThing() && !ce.getFiller().isOWLNothing()) {
			// TODO specification of a filler class
			logger.info("Specification of cardinalities not supported yet: " + ce);
			return;
		}

		OWLObjectProperty property = ce.getProperty().getNamedProperty();
		AbstractProperty vowlProperty = vowlData.getPropertyForIri(property.getIRI());
		vowlProperty.setMinCardinality(ce.getCardinality());
	}

	@Override
	public void visit(OWLDataExactCardinality ce) {
		OWLDataPropertyExpression property = ce.getProperty();

		if (property.isAnonymous()) {
			logger.info("Anonymous dataproperty for exact cardinality.");
			return;
		}

		AbstractProperty vowlProperty = vowlData.getPropertyForIri(property.asOWLDataProperty().getIRI());
		vowlProperty.setExactCardinality(ce.getCardinality());
	}

	@Override
	public void visit(OWLDataMaxCardinality ce) {
		OWLDataPropertyExpression property = ce.getProperty();

		if (property.isAnonymous()) {
			logger.info("Anonymous dataproperty for max cardinality.");
			return;
		}

		AbstractProperty vowlProperty = vowlData.getPropertyForIri(property.asOWLDataProperty().getIRI());
		vowlProperty.setMaxCardinality(ce.getCardinality());
	}

	@Override
	public void visit(OWLDataMinCardinality ce) {
		OWLDataPropertyExpression property = ce.getProperty();

		if (property.isAnonymous()) {
			logger.info("Anonymous dataproperty for min cardinality.");
			return;
		}

		AbstractProperty vowlProperty = vowlData.getPropertyForIri(property.asOWLDataProperty().getIRI());
		vowlProperty.setMinCardinality(ce.getCardinality());
	}

	@Override
	public void visit(OWLDataAllValuesFrom ce) {
		processDataValueRestriction(ce, PropertyAllSomeValue.ALL);
	}

	@Override
	public void visit(OWLDataSomeValuesFrom ce) {
		processDataValueRestriction(ce, PropertyAllSomeValue.SOME);
	}

	private void processDataValueRestriction(OWLQuantifiedDataRestriction ce, PropertyAllSomeValue value) {
		if (!ce.getFiller().isOWLDatatype()) {
			// TODO no datatype
			logger.info("DataValue range is not a datatype: " + ce);
			return;
		}


		OWLDatatype range = ce.getFiller().asOWLDatatype();
		OWLDataProperty restrictedProperty = ce.getProperty().asOWLDataProperty();
		DatatypeValueReference valueReference = vowlData.getGenerator().generateDatatypeValueReference(restrictedProperty.getIRI(), value);
		valueReference.addRange(vowlData.getGenerator().generateDatatypeReference(range.getIRI()).getIri());
		valueReference.addDomain(owlClass.getIRI());
	}

	@Override
	public void visit(OWLDataHasValue ce) {
		logger.info(ce + " not supported yet.");
	}

	@Override
	public void visit(OWLObjectAllValuesFrom ce) {
		processObjectValueRestriction(ce, PropertyAllSomeValue.ALL);
	}

	@Override
	public void visit(OWLObjectSomeValuesFrom ce) {
		processObjectValueRestriction(ce, PropertyAllSomeValue.SOME);
	}

	private void processObjectValueRestriction(OWLQuantifiedObjectRestriction ce, PropertyAllSomeValue value) {
		if (ce.getFiller().isAnonymous()) {
			// TODO anonymous
			logger.info("ObjectAllValuesFrom range class is anonymous: " + ce);
			return;
		}

		OWLClass rangeClass = ce.getFiller().asOWLClass();
		OWLObjectProperty restrictedProperty = ce.getProperty().getNamedProperty();
		ObjectValueReference objectValueReference = vowlData.getGenerator().generateObjectValueReference(restrictedProperty.getIRI(), value);
		objectValueReference.addRange(rangeClass.getIRI());
		objectValueReference.addDomain(owlClass.getIRI());
	}

	@Override
	public void visit(OWLObjectMaxCardinality ce) {
		if (!ce.getFiller().isOWLThing() && !ce.getFiller().isOWLNothing()) {
			// TODO specification of a filler class
			logger.info("Specification of cardinalities not supported yet: " + ce);
			return;
		}

		OWLObjectProperty property = ce.getProperty().getNamedProperty();
		AbstractProperty vowlProperty = vowlData.getPropertyForIri(property.getIRI());
		vowlProperty.setMaxCardinality(ce.getCardinality());
	}

	@Override
	public void visit(OWLObjectExactCardinality ce) {
		if (!ce.getFiller().isOWLThing() && !ce.getFiller().isOWLNothing()) {
			// TODO specification of a filler class
			logger.info("Specification of cardinalities not supported yet: " + ce);
			return;
		}

		OWLObjectProperty property = ce.getProperty().getNamedProperty();
		AbstractProperty vowlProperty = vowlData.getPropertyForIri(property.getIRI());
		vowlProperty.setExactCardinality(ce.getCardinality());
	}

	@Override
	public void visit(OWLObjectUnionOf ce) {
		Set<OWLClassExpression> operands = ce.getOperands();
		AbstractNode node = vowlData.getClassForIri(owlClass.getIRI());

		for (OWLClassExpression operand : operands) {
			if (!operand.isAnonymous()) {
				node.addElementToUnion(operand.asOWLClass().getIRI());
				node.addAttribute(VowlAttribute.UNION);
			} else {
				// TODO Anonymous undefined behaviour
				logger.info("Anonymous exists in unions.");
			}
		}
	}

	@Override
	public void visit(OWLObjectComplementOf ce) {
		if (ce.getOperand().isAnonymous()) {
			logger.info("Anonymous operand in object complement of.");
			return;
		}

		IRI baseClassIri = ce.getOperand().asOWLClass().getIRI();
		IRI complementIri = owlClass.getIRI();

		// TODO where to set the complement?
		//vowlData.getClassForIri(baseClassIri).addComplement(complementIri);
		vowlData.getClassForIri(complementIri).addComplement(baseClassIri);
		vowlData.getClassForIri(complementIri).addAttribute(VowlAttribute.COMPLEMENT);
	}

	@Override
	public void visit(OWLObjectIntersectionOf ce) {
		Set<OWLClassExpression> operands = ce.getOperands();
		AbstractNode node = vowlData.getClassForIri(owlClass.getIRI());

		for (OWLClassExpression operand : operands) {
			if (!operand.isAnonymous()) {
				node.addElementToIntersection(operand.asOWLClass().getIRI());
				node.addAttribute(VowlAttribute.INTERSECTION);
			} else {
				// TODO Anonymous undefined behaviour
				logger.info("Anonymous exists in intersections.");
			}
		}
	}

	@Override
	public void visit(OWLObjectOneOf ce) {
		ce.getIndividuals().forEach(owlIndividual -> owlIndividual.accept(new IndividualsVisitor(vowlData, owlIndividual, owlClass, vowlData
				.getOwlManager())));
	}
}
