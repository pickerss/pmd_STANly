/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */
package net.sourceforge.pmd.processor;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import net.sourceforge.pmd.PMD;
import net.sourceforge.pmd.PMDConfiguration;
import net.sourceforge.pmd.PMDException;
import net.sourceforge.pmd.Report;
import net.sourceforge.pmd.RuleContext;
import net.sourceforge.pmd.RuleSetFactory;
import net.sourceforge.pmd.RuleSets;
import net.sourceforge.pmd.SourceCodeProcessor;
import net.sourceforge.pmd.renderers.Renderer;
import net.sourceforge.pmd.util.datasource.DataSource;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.Logger;
import org.apache.log4j.Priority;

/**
 * @author Romain Pelisse <belaran@gmail.com>
 *
 */
public final class MonoThreadProcessor extends AbstractPMDProcessor {

	public MonoThreadProcessor(PMDConfiguration configuration) {
		super(configuration);
	}

	//private static final Log LOG = LogFactory.getLog(MonoThreadProcessor.class);
	private static final Logger LOG = Logger.getLogger(MonoThreadProcessor.class.getName());

	public void processFiles(RuleSetFactory ruleSetFactory, List<DataSource> files,
			RuleContext ctx, List<Renderer> renderers) {

		// single threaded execution

		RuleSets rs = createRuleSets(ruleSetFactory);
		SourceCodeProcessor processor = new SourceCodeProcessor(configuration);
		
		for (DataSource dataSource : files) {
			String niceFileName = filenameFrom(dataSource);
					
			Report report = PMD.setupReport(rs, ctx, niceFileName);
			
			if (LOG.isDebugEnabled()) {
				LOG.info("Processing " + ctx.getSourceCodeFilename());
			}
			rs.start(ctx);

			for (Renderer r : renderers) {
				r.startFileAnalysis(dataSource);
			}

			try {
				InputStream stream = new BufferedInputStream(dataSource.getInputStream());
				ctx.setLanguageVersion(null);
				processor.processSourceCode(stream, rs, ctx);
			} catch (PMDException pmde) {
				LOG.error( "Error while processing file", pmde.getCause());

				report.addError(new Report.ProcessingError(pmde.getMessage(), niceFileName));
			} catch (IOException ioe) {
				// unexpected exception: log and stop executor service
				addError(report, "Unable to read source file", ioe, niceFileName);
			} catch (RuntimeException re) {
				// unexpected exception: log and stop executor service
				addError(report, "RuntimeException while processing file", re, niceFileName);
			}

			rs.end(ctx);
			super.renderReports(renderers, ctx.getReport());
		}
	}

	private void addError(Report report, String msg, Exception ex, String fileName) {
		LOG.info(msg, ex);
		report.addError(
				new Report.ProcessingError(ex.getMessage(),
				fileName)
				);
	}
}
