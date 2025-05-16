document.addEventListener('DOMContentLoaded', function() {
    // Initialize the RDF graph visualization
    initRdfGraph();

    // Add event listeners for zoom controls
    document.getElementById('zoom-in').addEventListener('click', function() {
        network.zoom(1.2);
    });

    document.getElementById('zoom-out').addEventListener('click', function() {
        network.zoom(0.8);
    });

    document.getElementById('reset-view').addEventListener('click', function() {
        network.fit();
    });
});

let network = null;

function initRdfGraph() {
    const container = document.getElementById('rdf-graph');
    if (!container) return;

    // Fetch graph data from the API
    fetch('/api/rdf-graph-data')
        .then(response => response.json())
        .then(data => {
            // Transform the data for vis.js
            const visNodes = transformNodes(data.nodes);
            const visEdges = transformEdges(data.edges);

            // Create a vis.js dataset
            const nodes = new vis.DataSet(visNodes);
            const edges = new vis.DataSet(visEdges);

            // Create a network
            const networkData = {
                nodes: nodes,
                edges: edges
            };

            // Configuration for the network
            const options = {
                nodes: {
                    shape: 'dot',
                    size: 16,
                    font: {
                        size: 12,
                        face: 'Tahoma'
                    },
                    borderWidth: 2
                },
                edges: {
                    width: 1,
                    arrows: {
                        to: { enabled: true, scaleFactor: 0.5 }
                    },
                    font: {
                        size: 10,
                        align: 'middle'
                    },
                    color: {
                        color: '#848484',
                        highlight: '#1B5E20'
                    }
                },
                physics: {
                    stabilization: true,
                    barnesHut: {
                        gravitationalConstant: -2000,
                        centralGravity: 0.3,
                        springLength: 150,
                        springConstant: 0.04,
                        damping: 0.09
                    }
                },
                interaction: {
                    navigationButtons: true,
                    keyboard: true
                },
                groups: {
                    Book: {
                        color: { background: '#C8E6C9', border: '#1B5E20' },
                        shape: 'box'
                    },
                    User: {
                        color: { background: '#BBDEFB', border: '#0D47A1' },
                        shape: 'box'
                    },
                    Theme: {
                        color: { background: '#FFECB3', border: '#FF6F00' },
                        shape: 'diamond'
                    },
                    ReadingLevel: {
                        color: { background: '#F8BBD0', border: '#880E4F' },
                        shape: 'diamond'
                    },
                    Resource: {
                        color: { background: '#E1BEE7', border: '#4A148C' },
                        shape: 'dot'
                    }
                }
            };

            // Create the network
            network = new vis.Network(container, networkData, options);

            // Add event listeners
            network.on('click', function(params) {
                if (params.nodes.length > 0) {
                    const nodeId = params.nodes[0];
                    const node = nodes.get(nodeId);
                    console.log('Selected node:', node);
                    // You could show more details about the node here
                }
            });

            // Fit the network to the container
            network.fit();
        })
        .catch(error => {
            console.error('Error fetching RDF graph data:', error);
            container.innerHTML = '<div class="alert alert-danger">Failed to load RDF graph data</div>';
        });
}

function transformNodes(nodes) {
    return nodes.map(node => ({
        id: node.id,
        label: node.label,
        group: node.type || 'Resource',
        title: `${node.label} (${node.type || 'Resource'})`
    }));
}

function transformEdges(edges) {
    return edges.map((edge, index) => ({
        id: index,
        from: edge.source,
        to: edge.target,
        label: edge.label
    }));
}
