package nl.obren.sokrates.sourcecode.dependencies;

import nl.obren.sokrates.common.utils.RegexUtils;
import nl.obren.sokrates.sourcecode.SourceFile;
import nl.obren.sokrates.sourcecode.SourceFileFilter;
import nl.obren.sokrates.sourcecode.aspects.LogicalDecomposition;
import nl.obren.sokrates.sourcecode.aspects.SourceCodeAspect;
import nl.obren.sokrates.sourcecode.operations.ComplexOperation;
import org.apache.commons.lang3.StringUtils;

import java.util.*;

public class DependenciesFinderExtractor {
    private static final int MAX_SEARCH_DEPTH_LINES = 200;
    private LogicalDecomposition logicalDecomposition;
    private List<ComponentDependency> dependencies = new ArrayList<>();
    private Set<String> fileComponentDependencies = new HashSet<>();
    private Map<String, ComponentDependency> dependenciesMap = new HashMap<>();

    public DependenciesFinderExtractor(LogicalDecomposition logicalDecomposition) {
        this.logicalDecomposition = logicalDecomposition;
    }

    public List<ComponentDependency> findComponentDependencies(SourceCodeAspect aspect) {
        dependencies = new ArrayList<>();
        dependenciesMap = new HashMap<>();

        aspect.getSourceFiles().forEach(sourceFile -> findComponentDependenciesViaSimpleRules(sourceFile));
        aspect.getSourceFiles().forEach(sourceFile -> findComponentDependenciesViaMetaRules(sourceFile));

        return dependencies;
    }

    private void findComponentDependenciesViaSimpleRules(SourceFile sourceFile) {
        logicalDecomposition.getDependenciesFinder().getRules().forEach(rule -> {
            SourceFileFilter sourceFileFilter = new SourceFileFilter(rule.getPathPattern(), "");

            if (sourceFileFilter.pathMatches(sourceFile.getRelativePath())) {
                getLines(sourceFile).forEach(line -> {
                    if (RegexUtils.matchesEntirely(rule.getContentPattern(), line)) {
                        addDependency(dependencies, dependenciesMap, sourceFile, rule.getComponent());
                    }
                });
            }
        });
    }

    private void findComponentDependenciesViaMetaRules(SourceFile sourceFile) {
        logicalDecomposition.getDependenciesFinder().getMetaRules().forEach(metaRule -> {
            SourceFileFilter sourceFileFilter = new SourceFileFilter(metaRule.getPathPattern(), "");

            if (sourceFileFilter.pathMatches(sourceFile.getRelativePath())) {
                getLines(sourceFile).forEach(line -> {
                    if (RegexUtils.matchesEntirely(metaRule.getContentPattern(), line)) {
                        String component = new ComplexOperation(metaRule.getNameOperations()).exec(line);
                        addDependency(dependencies, dependenciesMap, sourceFile, component);
                    }
                });
            }
        });
    }

    private List<String> getLines(SourceFile sourceFile) {
        List<String> lines = sourceFile.getLines();
        if (lines.size() > MAX_SEARCH_DEPTH_LINES) {
            lines = lines.subList(0, MAX_SEARCH_DEPTH_LINES);
        }
        return lines;
    }

    private void addDependency(List<ComponentDependency> dependencies, Map<String, ComponentDependency> dependenciesMap, SourceFile sourceFile, String toComponent) {
        if (StringUtils.isBlank(toComponent)) {
            return;
        }

        String duplicationKey = sourceFile.getRelativePath() + " -> " + toComponent;
        if (fileComponentDependencies.contains(duplicationKey)) {
            return;
        }

        fileComponentDependencies.add(duplicationKey);

        ComponentDependency dependency = new ComponentDependency();
        String group = logicalDecomposition.getName();
        List<SourceCodeAspect> logicalComponents = sourceFile.getLogicalComponents(group);

        if (logicalComponents.size() == 0) {
            return;
        }

        SourceCodeAspect firstAspect = logicalComponents.get(0);
        dependency.setFromComponent(firstAspect.getName());
        dependency.setToComponent(toComponent);

        if (!dependency.getFromComponent().equalsIgnoreCase(dependency.getToComponent())) {
            String key = dependency.getDependencyString();
            if (dependenciesMap.containsKey(key)) {
                dependenciesMap.get(key).setCount(dependenciesMap.get(key).getCount() + 1);
            } else {
                dependencies.add(dependency);
                dependenciesMap.put(key, dependency);
            }
        }
    }
}
