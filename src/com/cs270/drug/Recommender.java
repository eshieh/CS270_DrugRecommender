package com.cs270.drug;

import java.io.InputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;

import org.apache.commons.io.output.NullOutputStream;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLIndividual;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectOneOf;
import org.semanticweb.owlapi.model.OWLObjectProperty;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.ConsoleProgressMonitor;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerConfiguration;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.SimpleConfiguration;

public class Recommender {
	
	/** CONSTANTS **/
	private static final String DRUG_IRI = "/drugs.owl";
	private static final String BASE = "http://www.semanticweb.org/yitao/ontologies/2014/2/untitled-ontology-16";
	private static final PrintStream SYSTEM_OUT = System.out;
	
	/** INSTANCE VARIABLES **/
	private OWLDataFactory _factory;
	private OWLReasoner _reasoner;

	/** METHODS **/
	public void run() {
		// Redirect system output
		System.setOut(new PrintStream(new NullOutputStream()));
		
		// Load owl ontology factory and reasoner
		try {
			init();
		} catch (OWLOntologyCreationException e) {
			consolePrint("Unable to load ontology " + BASE + " from " + DRUG_IRI);
			System.exit(1);
		}
		
		// Verify via the reasoner that the ontology is consistent
        _reasoner.precomputeInferences();
        if (!_reasoner.isConsistent()) {
        	consolePrint("Ontology " + BASE + " is inconsistent");
        	System.exit(1);
        }
		
		// Run main queries
        Scanner in = new Scanner(System.in);
        while (true) {
        	// Read in user inputs
            consolePrint("Enter any diseases you wish to treat (separated by commas): ");
            String diseaseStr = in.nextLine();
            
            consolePrint("Enter any symptoms you wish to treat (separated by commas): ");
            String symptomStr = in.nextLine();
            
            consolePrint("Enter any side effects you wish to avoid (separated by commas): ");
            String sideEffectStr = in.nextLine();
        	
        	// Process and display user inputs -> query reasoner
        	runQuery(diseaseStr.split(","), symptomStr.split(","), sideEffectStr.split(","));
        	
        	// Break when possible
        	consolePrint("Would you like to query again? If so, input a non-empty string: ");
        	if (in.nextLine().length() == 0)
        		break;
        }
        
        // Print TY message
        consolePrint("Thank you!");
	}
	
	private void init() throws OWLOntologyCreationException {
		// Get hold of an ontology manager, factory
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager();
        _factory = manager.getOWLDataFactory();
        
        // Now load the local copy
        InputStream is = Recommender.class.getResourceAsStream(DRUG_IRI);
        OWLOntology ont = manager.loadOntologyFromOntologyDocument(is);
        
        // Get some reason up in here
        OWLReasonerFactory rFactory = new Reasoner.ReasonerFactory();
        ConsoleProgressMonitor progressMonitor = new ConsoleProgressMonitor();
        OWLReasonerConfiguration config = new SimpleConfiguration(progressMonitor);
        _reasoner = rFactory.createReasoner(ont, config);
	}
	
    /**
     * Runs a query against drugs that treat a one of a set of diseases and symptoms
     * while avoiding specific side effects.
     * 
     * This is accomplished by first querying for all drugs that treat diseases and symptoms,
     * and then querying for the drugs among those that have the side effects.
     * The set of results from the second query is then subtracted from the first:
     * 
     * 	(Closed World Assumption logical layer on top of OWL's Open World Assumption)
     */
    public void runQuery(String[] diseaseStr, String[] symptomStr, String[] sideEffectStr) {
        // Instantiate parameters
    	Set<OWLIndividual> diseaseSet = createOWLIndividuals(diseaseStr);
    	Set<OWLIndividual> symptomSet = createOWLIndividuals(symptomStr);
    	Set<OWLIndividual> sideEffectSet = createOWLIndividuals(sideEffectStr);
        
        OWLObjectOneOf diseases = (diseaseSet.size() > 0) ? _factory.getOWLObjectOneOf(diseaseSet) : null;
        OWLObjectOneOf symptoms = (symptomSet.size() > 0) ? _factory.getOWLObjectOneOf(symptomSet) : null;
        OWLObjectOneOf sideEffects = (sideEffectSet.size() > 0) ? _factory.getOWLObjectOneOf(sideEffectSet) : null;
    	
        // Query for all drugs that treat diseases and symptoms. This is a query similar to:
        /*
         * Drugs and treatsDisease some {arthritis} and treatsSymptom some {fever}
         */
        OWLClass clsDrug = _factory.getOWLClass(IRI.create(BASE + "#Drugs"));
        OWLObjectProperty treatsDisease = _factory.getOWLObjectProperty(IRI.create(BASE + "#treatsDisease"));        
        OWLObjectProperty treatsSymptom = _factory.getOWLObjectProperty(IRI.create(BASE + "#treatsSymptom"));

        Set<OWLClassExpression> s1 = new HashSet<OWLClassExpression>();
        s1.add(clsDrug);
        if (diseases != null) {
        	OWLClassExpression expTreatsDisease = _factory.getOWLObjectSomeValuesFrom(treatsDisease, diseases);
        	s1.add(expTreatsDisease);
        }
        if (symptoms != null) {
        	OWLClassExpression expTreatsSymptom = _factory.getOWLObjectSomeValuesFrom(treatsSymptom, symptoms);
        	s1.add(expTreatsSymptom);
        }
        
        OWLObjectIntersectionOf q1 = _factory.getOWLObjectIntersectionOf(s1);
        Set<OWLNamedIndividual> q1Results = _reasoner.getInstances(q1, false).getFlattened();
        
        // Query for all of the above drugs that have at least one mentioned side effect. This is a query similar to:
        /*
         * Drugs and treatsDisease some {arthritis} and treatsSymptom some {fever} and hasSideEffect some {cold_sore}
         */
        // If there are no side effects to query for, then don't execute the second query
        if (sideEffects != null) {
	        OWLObjectProperty hasSideEffect = _factory.getOWLObjectProperty(IRI.create(BASE + "#hasSideEffect"));
	        OWLClassExpression expHasSideEffect = _factory.getOWLObjectSomeValuesFrom(hasSideEffect, sideEffects);
	        
	        Set<OWLClassExpression> s2 = s1;
	        s2.add(expHasSideEffect);
	        
	        OWLObjectIntersectionOf q2 = _factory.getOWLObjectIntersectionOf(s2);
	        Set<OWLNamedIndividual> q2Results = _reasoner.getInstances(q2, false).getFlattened();
	        
	        // We want the set difference of the two in order to enforce Closed World Assumption
	        q1Results.removeAll(q2Results);
        }
        
        // Process fragments
        ArrayList<String> results = new ArrayList<String>();
        for (OWLNamedIndividual in : q1Results) {
	    	results.add(in.getIRI().getFragment());
	    }
        
        // Print results
        consolePrint("\n");
        if (results.size() == 0) {
		    consolePrint("Unable to find any drugs in our ontology that match that criteria");
        } else {
        	String prefix = (results.size() == 1) 
        			? "You might find the following drug useful: "
        			: "You might find the following drugs useful: ";
        	consolePrint(prefix + results.toString());
        }
    }
    
    private Set<OWLIndividual> createOWLIndividuals(String[] input) {
    	Set<OWLIndividual> result = new HashSet<OWLIndividual>();
    	for (int i = 0; i < input.length; i++) {
    		// Verify that the inputs are valid
    		String name = input[i].trim();
    		if (name.length() == 0)
    			continue;
    		
    		// Construct OWLIndividual from ontology
    		IRI iri = IRI.create(BASE + "#" + name);
    		result.add(_factory.getOWLNamedIndividual(iri));
    	}
    	return result;
    }
    
    public static void consolePrint(String input){
	     SYSTEM_OUT.println(input);
	}
    
    /**
	 * @param args
	 */
	public static void main(String[] args) {
		new Recommender().run();
	}

}
