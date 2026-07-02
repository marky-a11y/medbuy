/**
 * Highlights the benchmark value (Low/Normal/High) closest to
 * each KPI's actual value when the recommendation dialog opens.
 */
function highlightClosestBenchmark() {
    setTimeout(function() {
        var table = document.querySelector('#opportunityMetricsTable');
        if (!table) return;
        var rows = table.querySelectorAll('tbody tr');
        for (var r = 0; r < rows.length; r++) {
            var cells = rows[r].querySelectorAll('td');
            if (cells.length < 6) continue;

            // cell[0]=name, cell[1]=value, cell[2]=Low, cell[3]=Normal, cell[4]=High, cell[5]=status
            var valueText = cells[1].textContent.trim();
            var lowText = cells[2].textContent.trim();
            var normText = cells[3].textContent.trim();
            var highText = cells[4].textContent.trim();

            var actual = parseFloat(valueText.replace(/[\$,%]/g, ''));
            var low = parseFloat(lowText.replace(/[\$,%]/g, ''));
            var norm = parseFloat(normText.replace(/[\$,%]/g, ''));
            var high = parseFloat(highText.replace(/[\$,%]/g, ''));

            if (isNaN(actual) || isNaN(low) || isNaN(norm) || isNaN(high)) continue;

            var distLow = Math.abs(actual - low);
            var distNorm = Math.abs(actual - norm);
            var distHigh = Math.abs(actual - high);
            var minDist = Math.min(distLow, distNorm, distHigh);

            var closestIdx = 2;
            var color = '#b71c1c';
            if (distNorm === minDist) { closestIdx = 3; color = '#1565c0'; }
            if (distHigh === minDist) { closestIdx = 4; color = '#2e7d32'; }

            var closestCell = cells[closestIdx];
            closestCell.style.outline = '3px solid ' + color;
            closestCell.style.outlineOffset = '-2px';
            closestCell.style.borderRadius = '3px';
        }
    }, 200);
}
