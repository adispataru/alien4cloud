package org.alien4cloud.alm.deployment.configuration.flow.modifiers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.alien4cloud.alm.deployment.configuration.flow.FlowExecutionContext;
import org.alien4cloud.alm.deployment.configuration.flow.ITopologyModifier;
import org.alien4cloud.tosca.model.definitions.DeploymentArtifact;
import org.alien4cloud.tosca.model.templates.AbstractTemplate;
import org.alien4cloud.tosca.model.templates.NodeTemplate;
import org.alien4cloud.tosca.model.templates.Topology;
import org.apache.commons.collections4.MapUtils;
import org.elasticsearch.common.collect.Lists;

import com.google.common.collect.Maps;

import alien4cloud.utils.InputArtifactUtil;
import org.springframework.stereotype.Component;

/**
 * Inject input artifacts into the topology.
 */
@Component
public class InputArtifactsModifier implements ITopologyModifier {

    @Override
    public void process(Topology topology, FlowExecutionContext context) {

    }

    /**
     * Inject input artifacts in the corresponding nodes.
     * 
     * @param topology The topology in which to inject input artifacts.
     * @param inputArtifacts The input artifacts to inject in the topology.
     */
    private void processInputArtifacts(Topology topology, Map<String, DeploymentArtifact> inputArtifacts) {
        if (topology.getInputArtifacts() != null && !topology.getInputArtifacts().isEmpty()) {
            // we'll build a map inputArtifactId -> List<DeploymentArtifact>
            Map<String, List<DeploymentArtifact>> artifactMap = Maps.newHashMap();
            // iterate over nodes in order to remember all nodes referencing an input artifact
            for (NodeTemplate nodeTemplate : topology.getNodeTemplates().values()) {
                if (MapUtils.isNotEmpty(nodeTemplate.getArtifacts())) {
                    processInputArtifactForTemplate(artifactMap, nodeTemplate);
                }
                if (MapUtils.isNotEmpty(nodeTemplate.getRelationships())) {
                    nodeTemplate.getRelationships().entrySet().stream()
                            .filter(relationshipTemplateEntry -> MapUtils.isNotEmpty(relationshipTemplateEntry.getValue().getArtifacts()))
                            .forEach(relationshipTemplateEntry -> processInputArtifactForTemplate(artifactMap, relationshipTemplateEntry.getValue()));
                }
            }

            // Override the artifacts definition in the topology with the input artifact data.
            Map<String, DeploymentArtifact> allInputArtifact = new HashMap<>();
            allInputArtifact.putAll(topology.getInputArtifacts());
            if (MapUtils.isNotEmpty(inputArtifacts)) {
                allInputArtifact.putAll(inputArtifacts);
            }
            for (Map.Entry<String, DeploymentArtifact> inputArtifact : allInputArtifact.entrySet()) {
                List<DeploymentArtifact> nodeArtifacts = artifactMap.get(inputArtifact.getKey());
                if (nodeArtifacts != null) {
                    for (DeploymentArtifact nodeArtifact : nodeArtifacts) {
                        nodeArtifact.setArtifactRef(inputArtifact.getValue().getArtifactRef());
                        nodeArtifact.setArtifactName(inputArtifact.getValue().getArtifactName());
                        nodeArtifact.setArtifactRepository(inputArtifact.getValue().getArtifactRepository());
                        nodeArtifact.setRepositoryName(inputArtifact.getValue().getRepositoryName());
                        nodeArtifact.setRepositoryCredential(inputArtifact.getValue().getRepositoryCredential());
                        nodeArtifact.setRepositoryURL(inputArtifact.getValue().getRepositoryURL());
                        nodeArtifact.setArchiveName(inputArtifact.getValue().getArchiveName());
                        nodeArtifact.setArchiveVersion(inputArtifact.getValue().getArchiveVersion());
                    }
                }
            }
        }
    }

    /**
     * Map the template's artifacts associated with an input to into the given artifactMap.
     *
     * @param artifactMap The map of inputArtifactId -> List of associated artifacts into which to map the given template's artifacts.
     * @param template The template for which to map artifact.
     */
    private static void processInputArtifactForTemplate(Map<String, List<DeploymentArtifact>> artifactMap, AbstractTemplate template) {
        for (DeploymentArtifact da : template.getArtifacts().values()) {
            String inputArtifactId = InputArtifactUtil.getInputArtifactId(da);
            if (inputArtifactId != null) {
                List<DeploymentArtifact> das = artifactMap.get(inputArtifactId);
                if (das == null) {
                    das = Lists.newArrayList();
                    artifactMap.put(inputArtifactId, das);
                }
                das.add(da);
            }
        }
    }
}
