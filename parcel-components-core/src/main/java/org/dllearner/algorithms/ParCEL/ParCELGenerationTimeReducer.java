package org.dllearner.algorithms.ParCEL;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.dllearner.core.owl.Individual;

/**
 * Compact two a partial definitions using Generation Time Greedy strategy
 * 
 * @author An C. Tran
 * 
 */

public class ParCELGenerationTimeReducer implements ParCELReducer {

	@Override
	public SortedSet<ParCELExtraNode> reduce(SortedSet<ParCELExtraNode> partialDefinitions,
			Set<Individual> positiveExamples) {
		return reduce(partialDefinitions, positiveExamples, 0);
	}

	@Override
	public SortedSet<ParCELExtraNode> reduce(SortedSet<ParCELExtraNode> partialDefinitions,
			Set<Individual> positiveExamples, int uncoveredPositiveExamples) {
		Set<Individual> positiveExamplesTmp = new HashSet<Individual>();
		positiveExamplesTmp.addAll(positiveExamples);

		TreeSet<ParCELExtraNode> newSortedPartialDefinitions = new TreeSet<ParCELExtraNode>(
				new ParCELDefinitionGenerationTimeComparator());

		synchronized (partialDefinitions) {
			newSortedPartialDefinitions.addAll(partialDefinitions);
		}

		TreeSet<ParCELExtraNode> minimisedPartialDefinition = new TreeSet<ParCELExtraNode>(
				new ParCELDefinitionGenerationTimeComparator());

		Iterator<ParCELExtraNode> partialDefinitionIterator = newSortedPartialDefinitions.iterator();
		
		while ((positiveExamplesTmp.size() > uncoveredPositiveExamples)
				&& (partialDefinitionIterator.hasNext())) {
			ParCELExtraNode node = partialDefinitionIterator.next();

			int positiveExamplesRemoved = positiveExamplesTmp.size();
			positiveExamplesTmp.removeAll(node.getCoveredPositiveExamples());

			positiveExamplesRemoved -= positiveExamplesTmp.size();

			if (positiveExamplesRemoved > 0) {
				node.setCorrectness(positiveExamplesRemoved);
				minimisedPartialDefinition.add(node);
			}
		}

		return minimisedPartialDefinition;
	}

}
