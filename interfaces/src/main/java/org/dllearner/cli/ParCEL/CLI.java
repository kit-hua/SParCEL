package org.dllearner.cli.ParCEL;

import java.io.File;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;


import org.apache.xmlbeans.XmlObject;
import org.dllearner.algorithms.ParCEL.ParCELPosNegLP;
import org.dllearner.cli.CrossValidation;
import org.dllearner.algorithms.ParCEL.ParCELReducer;
import org.dllearner.cli.ParCEL.CELOEFortifiedCrossValidation3PhasesFair;
import org.dllearner.cli.ParCEL.ParCELCrossValidation;
import org.dllearner.cli.ParCEL.ParCELExFortifiedCrossValidation3Phases;
import org.dllearner.cli.ParCEL.ParCELFortifiedCrossValidation2PhasesFair;
import org.dllearner.algorithms.ParCELEx.ParCELExAbstract;
import org.dllearner.configuration.IConfiguration;
import org.dllearner.configuration.spring.ApplicationContextBuilder;
import org.dllearner.configuration.spring.DefaultApplicationContextBuilder;
import org.dllearner.configuration.util.SpringConfigurationXMLBeanConverter;
import org.dllearner.confparser3.ConfParserConfiguration; 
import org.dllearner.confparser3.ParseException;
import org.dllearner.core.*;
import org.dllearner.core.config.ConfigOption;
import org.dllearner.learningproblems.PosNegLP;
import org.dllearner.utilities.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;


/**
 * 
 * Command line interface for learning algorithms.
 * Adopted from DLLearner CLI with extensions for supporting more learning options + fortification
 * 
 * @author An C. Tran
 * 
 */
public class CLI {

	private static Logger logger = LoggerFactory.getLogger(CLI.class);
	
	private ApplicationContext context;
	private IConfiguration configuration;
	private File confFile;
	
	private LearningAlgorithm algorithm;
	private KnowledgeSource knowledgeSource;
	
	// some CLI options
	private boolean writeSpringConfiguration = false;
	private boolean performCrossValidation = false;
	
	
	@ConfigOption(name = "nrOfFolds", defaultValue="10", description="Number of validation folds")
	private int nrOfFolds = 10;			//default 10 folds (for cross validation), it can be changed using configuration option
	
	@ConfigOption(name = "noOfRuns", defaultValue="1", description="Number of runs for k-fold cross validation")
	private int noOfRuns = 1;			//how many "runs" of k-fold cross validation 
	
	
	@ConfigOption(name = "fortification", defaultValue="false", description="Use this to evaluate the fortification")
	private boolean fortification = false;
	
	@ConfigOption(name = "fortificationTimeout", defaultValue="0", description="Use this to indicate timeout of learning fortification definition. Default is no timeout")
	private int fortificationTimeout = 0;
	
	@ConfigOption(name = "fairComparison", defaultValue="false", description="Use this learning double timeout for non-fortification strategy.")
	private boolean fairComparison = false;
	
	@ConfigOption(name = "fortificationNoise", defaultValue="98%", description="Indicate minimal coverage for fortification definitions")
	private double fortificationNoise = 98;

	@ConfigOption(name = "reducers", defaultValue="null", description="Use this indicate the reducer (used for ParCEL and ParCELEx only)")
	private Set<ParCELReducer> reducers = null;

	
	public CLI() {
		
	}
	
	public CLI(File confFile) {
		this();
		this.confFile = confFile;
	}
	
	// separate init methods, because some scripts may want to just get the application
	// context from a conf file without actually running it
	public void init() throws IOException {
    	   	
    	if(context == null) {
    		Resource confFileR = new FileSystemResource(confFile);
    		List<Resource> springConfigResources = new ArrayList<Resource>();
            configuration = new ConfParserConfiguration(confFileR);

            ApplicationContextBuilder builder = new DefaultApplicationContextBuilder();
            context =  builder.buildApplicationContext(configuration, springConfigResources);	
    	}
	}
	
	
    public void run() throws IOException { 

    	if (writeSpringConfiguration) {
        	SpringConfigurationXMLBeanConverter converter = new SpringConfigurationXMLBeanConverter();
        	XmlObject xml;
        	if(configuration == null) {
        		Resource confFileR = new FileSystemResource(confFile);
        		configuration = new ConfParserConfiguration(confFileR);
        		xml = converter.convert(configuration);
        	} else {
        		xml = converter.convert(configuration);
        	}
        	String springFilename = confFile.getCanonicalPath().replace(".conf", ".xml");
        	File springFile = new File(springFilename);
        	if(springFile.exists()) {
        		logger.warn("Cannot write Spring configuration, because " + springFilename + " already exists.");
        	} else {
        		Files.createFile(springFile, xml.toString());
        	}		
		}    	 
    	
    	//cross validation
		if (performCrossValidation) {
			AbstractReasonerComponent rs = context.getBean(AbstractReasonerComponent.class);
			AbstractCELA la = context.getBean(AbstractCELA.class);
			
			//---------------------------
			//ParCEL, ParCELEx
			//---------------------------
			//this test is added for ParCEL/Ex algorithm since it does not use the PosNegLP
			//use "check and fail" strategy (better strategies? ==> use instance check)
			try {
				ParCELPosNegLP lp = context.getBean(ParCELPosNegLP.class);		

				logger.info("\\\\-----------------------------------\n\\\\" + nrOfFolds + 
						" folds\n\\\\-----------------------------------");

				//---------------------------
				//with FORTIFICATION
				//---------------------------
				if (this.fortification) {
					logger.info("Cross validation with FORTIFICATION");
					
					if (la instanceof ParCELExAbstract)
						new ParCELExFortifiedCrossValidation3Phases(la, lp, rs, nrOfFolds, false, noOfRuns);
					else 
						new ParCELFortifiedCrossValidation2PhasesFair(la, lp, rs, nrOfFolds, 
								false, noOfRuns, fortificationTimeout, fairComparison);
				}
				
				//---------------------------
				//no FORTIFICATION
				//---------------------------
				else {	//without fortification, the cross-validation is similar between ParCEL and ParCEL-Ex
					logger.info("Cross validation WITHOUT FORTIFICATION");					
					new ParCELCrossValidation(la, lp, rs, nrOfFolds, false, noOfRuns); 
				}
				
				//int noOfFolds[] = {10}; 	//{4, 5, 8, 10};
				//for (int f=0; f < noOfFolds.length; f++) {				
					//new ParCELFortifiedCrossValidationOrtho2Blind(la, lp, rs, noOfFolds[f], false, noOfRuns);					
					//new ParCELFortifiedCrossValidation2Phases(la, lp, rs, noOfFolds[f], false, noOfRuns);
					//new ParCELExFortifiedCrossValidation3Phases(la, lp, rs, nrOfFolds, false, noOfRuns);
					//new ParCELCrossValidation(la, lp, rs, nrOfFolds, false, noOfRuns);
				//}
				//else				 
					//new ParCELCrossValidation(la, lp, rs, nrOfFolds, false, noOfRuns);
					//new ParCELValidationModelAnalysis(la, lp, rs, nrOfFolds, false, noOfRuns);
					//new ParCELFortifiedCrossValidation(la, lp, rs, nrOfFolds, false, noOfRuns);
					//new ParCELValidationMultiReducers(la, lp, rs, nrOfFolds, false, noOfRuns, reducers);
			}
			catch (BeansException be) {
				PosNegLP lp = context.getBean(PosNegLP.class);
				
				//int noOfFolds[] = {4, 5, 8, 10};
				//int noOfFolds[] = {10};
				//for (int f=0; f < noOfFolds.length; f++) {				
					logger.info("\\\\-----------------------------------\n\\\\" + nrOfFolds + 
							" folds\n\\\\-----------------------------------");
					//new CELOEFortifiedCrossValidationBlind(la, lp, rs, noOfFolds[f], false, noOfRuns);
					
					
					
					if (this.fortification) {
						logger.info("Cross validation with FORTIFICATION");
						new CELOEFortifiedCrossValidation3PhasesFair(la, lp, rs, nrOfFolds, false, noOfRuns, 
								fortificationNoise, fortificationTimeout, fairComparison);
					}
					else {
						logger.info("Cross validation with NO FORTIFICATION");
						new CrossValidation(la, lp, rs, nrOfFolds, false, noOfRuns);
					}
				}
	
			
			
		//no cross validation
		} else {
			
			for(Entry<String, LearningAlgorithm> entry : context.getBeansOfType(LearningAlgorithm.class).entrySet()){
				algorithm = entry.getValue();
				logger.info("Running algorithm instance \"" + entry.getKey() + "\" (" + algorithm.getClass().getSimpleName() + ")");
				algorithm.start();
			}

			
		}

    }

    public boolean isWriteSpringConfiguration() {
		return writeSpringConfiguration;
	}

	public void setWriteSpringConfiguration(boolean writeSpringConfiguration) {
		this.writeSpringConfiguration = writeSpringConfiguration;
	}    
    
	/**
	 * @param args
	 * @throws ParseException 
	 * @throws IOException 
	 * @throws ReasoningMethodUnsupportedException 
	 */
	public static void main(String[] args) throws ParseException, IOException, ReasoningMethodUnsupportedException {
   	
		System.out.println("DL-Learner (ParCEL) command line interface");
		
		// currently, CLI has exactly one parameter - the conf file
		if(args.length == 0) {
			System.out.println("You need to give a conf file as argument.");
			System.exit(0);
		}
		
		// read file and print and print a message if it does not exist
		File file = new File(args[args.length - 1]);
		if(!file.exists()) {
			System.out.println("File \"" + file + "\" does not exist.");
			System.exit(0);			
		}
		
		logger.info("Learning config file: " + file.getName());
		
		Resource confFile = new FileSystemResource(file);
		
		List<Resource> springConfigResources = new ArrayList<Resource>();

        try {
            //DL-Learner Configuration Object
            IConfiguration configuration = new ConfParserConfiguration(confFile);

            
            //parsing learning configuration (application context) 
            ApplicationContextBuilder builder = new DefaultApplicationContextBuilder();
            ApplicationContext context =  builder.buildApplicationContext(configuration,springConfigResources);

            //get CLI from the configuration 
            CLI cli;
            if(context.containsBean("cli")) {
                cli = (CLI) context.getBean("cli");
            } else {
                cli = new CLI();
            }

            cli.setContext(context);
            cli.setConfFile(file);
            cli.run();
            
        } catch (Exception e) {
            String stacktraceFileName = "log/error.log";

            // Get the Root Error Message
            logger.error("An Error Has Occurred During Processing.");
            logger.error(e.getMessage());
            logger.debug("Stack Trace: ", e);
            logger.error("Terminating DL-Learner...and writing stacktrace to: " + stacktraceFileName);
            FileOutputStream fos = new FileOutputStream(stacktraceFileName);
            PrintStream ps = new PrintStream(fos);
            e.printStackTrace(ps);
            
        }

    }



    public void setContext(ApplicationContext context) {
		this.context = context;
	}

	public ApplicationContext getContext() {
		return context;
	}

	public File getConfFile() {
		return confFile;
	}

	public void setConfFile(File confFile) {
		this.confFile = confFile;
	}

	public boolean isPerformCrossValidation() {
		return performCrossValidation;
	}

	public void setPerformCrossValidation(boolean performCrossValiation) {
		this.performCrossValidation = performCrossValiation;
	}

	public int getNrOfFolds() {
		return nrOfFolds;
	}

	public void setNrOfFolds(int nrOfFolds) {
		this.nrOfFolds = nrOfFolds;
	}
	
	public LearningAlgorithm getLearningAlgorithm() {
		return algorithm;
	}
	
	public KnowledgeSource getKnowledgeSource() {
		return knowledgeSource;
	}
	
	
	public int getNoOfRuns() {
		return noOfRuns;
	}

	public void setNoOfRuns(int noOfRuns) {
		this.noOfRuns = noOfRuns;
	}
	
	
	public boolean getFortification() {
		return this.fortification;
	}
	
	public void setFortification(boolean fort) {
		this.fortification = fort;
	}
	
	
	public Set<ParCELReducer> getReducers() {
		return reducers;
	}

	public void setReducers(Set<ParCELReducer> reducers) {
		this.reducers = reducers;
	}
	
	public void setFortificationTimeout(int time) {
		this.fortificationTimeout = time;
	}
	
	public int getFortificationTimeout() {
		return this.fortificationTimeout;
	}
	
	public boolean getFairComparison() {
		return this.fairComparison;
	}
	
	public void setFairComparison(boolean fair) {
		this.fairComparison = fair;
	}

	public double getFortificationNoise() {
		return fortificationNoise;
	}

	public void setFortificationNoise(double fortificationNoise) {
		this.fortificationNoise = fortificationNoise;
	}

	
}
