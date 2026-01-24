(function() {
    const urlParams = new URLSearchParams(window.location.search);
    const recordId = urlParams.get('id');

    if (!recordId) {
        showError('No provenance record ID provided. Add ?id=<uuid> to the URL.');
        return;
    }

    const svg = d3.select('#graph');
    const container = document.getElementById('graph-container');
    const tooltip = document.getElementById('tooltip');
    const info = document.getElementById('info');
    const depthInput = document.getElementById('depth');
    const reloadButton = document.getElementById('reload');
    const legend = document.getElementById('legend');

    let simulation;
    let currentGraph = null;

    const typeColors = d3.scaleOrdinal(d3.schemeCategory10);

    function showLoading() {
        const existing = container.querySelector('.loading, .error');
        if (existing) existing.remove();
        const loading = document.createElement('div');
        loading.className = 'loading';
        loading.textContent = 'Loading graph...';
        container.appendChild(loading);
    }

    function showError(message) {
        const existing = container.querySelector('.loading, .error');
        if (existing) existing.remove();
        const error = document.createElement('div');
        error.className = 'error';
        error.textContent = message;
        container.appendChild(error);
    }

    function clearMessages() {
        const existing = container.querySelector('.loading, .error');
        if (existing) existing.remove();
    }

    async function loadGraph() {
        const depth = parseInt(depthInput.value) || 10;
        showLoading();

        try {
            const response = await fetch(`/api/provenance/${recordId}/graph?depth=${depth}`);
            if (!response.ok) {
                if (response.status === 404) {
                    showError(`Provenance record not found: ${recordId}`);
                } else {
                    showError(`Failed to load graph: ${response.statusText}`);
                }
                return;
            }

            currentGraph = await response.json();
            clearMessages();
            renderGraph(currentGraph);
            updateInfo(currentGraph);
            updateLegend(currentGraph);
        } catch (err) {
            showError(`Error loading graph: ${err.message}`);
        }
    }

    function updateInfo(graph) {
        const meta = graph.metadata;
        let text = `Nodes: ${meta.totalNodes} | Max Depth: ${meta.maxDepth}`;
        if (meta.depthLimitReached) {
            text += ' (limit reached)';
        }
        if (meta.missingPredecessors && meta.missingPredecessors.length > 0) {
            text += ` | Missing: ${meta.missingPredecessors.length}`;
        }
        info.textContent = text;
    }

    function updateLegend(graph) {
        const types = [...new Set(graph.nodes.map(n => n.dataType))].sort();

        legend.innerHTML = '<h3>Node Types</h3>';
        types.forEach(type => {
            const color = typeColors(type);
            const item = document.createElement('div');
            item.className = 'legend-item';
            item.innerHTML = `
                <svg width="20" height="20">
                    <circle cx="10" cy="10" r="8" fill="${color}" stroke="${d3.color(color).darker(0.5)}" stroke-width="2"/>
                </svg>
                <span>${type || 'Unknown'}</span>
            `;
            legend.appendChild(item);
        });
    }

    function renderGraph(graph) {
        svg.selectAll('*').remove();

        const width = container.clientWidth;
        const height = container.clientHeight;

        svg.attr('viewBox', [0, 0, width, height]);

        const links = graph.edges.map(e => ({
            source: e.sourceId,
            target: e.targetId
        }));

        const nodes = graph.nodes.map(n => ({ ...n }));

        const g = svg.append('g');

        const zoom = d3.zoom()
            .scaleExtent([0.1, 4])
            .on('zoom', (event) => {
                g.attr('transform', event.transform);
            });

        svg.call(zoom);

        svg.append('defs').append('marker')
            .attr('id', 'arrowhead')
            .attr('viewBox', '-0 -5 10 10')
            .attr('refX', 20)
            .attr('refY', 0)
            .attr('orient', 'auto')
            .attr('markerWidth', 6)
            .attr('markerHeight', 6)
            .append('path')
            .attr('d', 'M 0,-5 L 10,0 L 0,5')
            .attr('fill', '#999');

        const link = g.append('g')
            .attr('class', 'links')
            .selectAll('line')
            .data(links)
            .join('line')
            .attr('class', 'link')
            .attr('marker-end', 'url(#arrowhead)');

        const node = g.append('g')
            .attr('class', 'nodes')
            .selectAll('g')
            .data(nodes)
            .join('g')
            .attr('class', 'node')
            .call(d3.drag()
                .on('start', dragstarted)
                .on('drag', dragged)
                .on('end', dragended));

        node.each(function(d) {
            const el = d3.select(this);
            const color = typeColors(d.dataType);
            const strokeColor = d3.color(color).darker(0.5);

            el.append('circle')
                .attr('r', 12 + Math.min(d.predecessorCount, 10))
                .attr('fill', color)
                .attr('stroke', strokeColor)
                .attr('stroke-width', 2);
        });

        node.append('text')
            .attr('dy', d => 22 + Math.min(d.predecessorCount, 10))
            .attr('text-anchor', 'middle')
            .text(d => truncate(d.dataType, 12));

        node.on('mouseover', (event, d) => showTooltip(event, d))
            .on('mouseout', hideTooltip)
            .on('click', (event, d) => highlightConnected(d));

        simulation = d3.forceSimulation(nodes)
            .force('link', d3.forceLink(links).id(d => d.id).distance(100))
            .force('charge', d3.forceManyBody().strength(-400))
            .force('center', d3.forceCenter(width / 2, height / 2))
            .force('y', d3.forceY().y(d => 100 + d.depth * 120).strength(0.3))
            .on('tick', ticked);

        function ticked() {
            link
                .attr('x1', d => d.source.x)
                .attr('y1', d => d.source.y)
                .attr('x2', d => d.target.x)
                .attr('y2', d => d.target.y);

            node.attr('transform', d => `translate(${d.x},${d.y})`);
        }

        function dragstarted(event) {
            if (!event.active) simulation.alphaTarget(0.3).restart();
            event.subject.fx = event.subject.x;
            event.subject.fy = event.subject.y;
        }

        function dragged(event) {
            event.subject.fx = event.x;
            event.subject.fy = event.y;
        }

        function dragended(event) {
            if (!event.active) simulation.alphaTarget(0);
            event.subject.fx = null;
            event.subject.fy = null;
        }

        svg.call(zoom.transform, d3.zoomIdentity);
    }

    function showTooltip(event, d) {
        const signingTime = d.signingTime ? new Date(d.signingTime).toLocaleString() : 'N/A';

        tooltip.innerHTML = `
            <div class="title">${d.dataType || 'Unknown'}</div>
            <div class="row"><span class="label">ID:</span><span class="value">${d.id}</span></div>
            <div class="row"><span class="label">Data ID:</span><span class="value">${d.dataId || 'N/A'}</span></div>
            <div class="row"><span class="label">Signed:</span><span class="value">${signingTime}</span></div>
            <div class="row"><span class="label">Depth:</span><span class="value">${d.depth}</span></div>
            <div class="row"><span class="label">Predecessors:</span><span class="value">${d.predecessorCount}</span></div>
        `;

        tooltip.style.left = (event.pageX + 15) + 'px';
        tooltip.style.top = (event.pageY + 15) + 'px';
        tooltip.classList.add('visible');
    }

    function hideTooltip() {
        tooltip.classList.remove('visible');
    }

    function highlightConnected(d) {
        if (!currentGraph) return;

        const connectedIds = new Set([d.id]);
        currentGraph.edges.forEach(e => {
            if (e.sourceId === d.id) connectedIds.add(e.targetId);
            if (e.targetId === d.id) connectedIds.add(e.sourceId);
        });

        svg.selectAll('.node')
            .style('opacity', n => connectedIds.has(n.id) ? 1 : 0.3);

        svg.selectAll('.link')
            .style('opacity', l => (l.source.id === d.id || l.target.id === d.id) ? 1 : 0.1);

        setTimeout(() => {
            svg.selectAll('.node').style('opacity', 1);
            svg.selectAll('.link').style('opacity', 1);
        }, 2000);
    }

    function truncate(str, maxLen) {
        if (!str) return '';
        return str.length > maxLen ? str.substring(0, maxLen - 1) + '...' : str;
    }

    reloadButton.addEventListener('click', loadGraph);
    depthInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') loadGraph();
    });

    loadGraph();
})();
