package isabelle.eclipse.launch.config;

import java.util.Arrays;
import java.util.List;

import isabelle.eclipse.core.IsabelleCorePlugin;
import isabelle.eclipse.core.app.Isabelle;
import isabelle.eclipse.launch.IsabelleLaunchConstants;
import isabelle.eclipse.launch.IsabelleLaunchPlugin;
import isabelle.scala.IsabelleSystemFacade;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;

public abstract class IsabelleLaunch extends LaunchConfigurationDelegate {

	@Override
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		
		if (monitor.isCanceled()) {
			return;
		}
		
		Isabelle isabelle = IsabelleCorePlugin.getIsabelle();
		if (isabelle.isRunning()) {
        	// we only allow one prover instance
        	abort("Only a single prover can be running at any time - stop the running prover before launching a new one");
        	return;
        }
		
		
		if (monitor.isCanceled()) {
			return;
		}

		String path = getInstallationPath(configuration);

		if (monitor.isCanceled()) {
			return;
		}
		
		
		String logic = getLogicConfig(configuration);
		verifyLogic(path, logic);
		
		if (monitor.isCanceled()) {
			return;
		}

		monitor.beginTask("Launching " + configuration.getName() + "...", IProgressMonitor.UNKNOWN);
		
		isabelle.start(path, logic);
		
		System.out.println("Done launching");
		
	}
	
	public static String getLogicConfig(ILaunchConfiguration configuration) {
		String logic = ""; //$NON-NLS-1$
		try {
			logic = configuration.getAttribute(IsabelleLaunchConstants.ATTR_LOGIC, ""); //$NON-NLS-1$
		} catch (CoreException ce) {
			IsabelleLaunchPlugin.log("Error reading configuration", ce);
		}
		return logic;
	}
	
	
	private static void verifyLogic(String path, String logic) 
		throws CoreException {
		
		if (logic == null || logic.isEmpty()) {
			abort("Isabelle logic not specified");
		}
		
		List<String> logics = Arrays.asList(loadLogics(path));
		if (!logics.contains(logic)) {
			abort("Invalid Isabelle logic specified");
		}
		
	}
	
    public static String[] loadLogics(String path) {
    	if (path == null || path.isEmpty()) {
    		return new String[0];
    	}
    	
    	try {
    		IsabelleSystemFacade isabelle = new IsabelleSystemFacade(path);
    		return isabelle.findLogics();
    	} catch (Exception ex) {
    		return new String[0];
    		IsabelleLaunchPlugin.log("Unable to launch Isabelle at path: " + path, ex);
    	}
    	
    }
	
    protected abstract String getInstallationPath(ILaunchConfiguration configuration) throws CoreException ;
    
	/**
	 * Throws a core exception with an error status object built from
	 * the given message, lower level exception, and error code.
	 * @param message the status message
	 * @param exception lower level exception associated with the
	 *  error, or <code>null</code> if none
	 * @param code error code
	 */
	protected static void abort(String message, Throwable exception, int code) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, IsabelleLaunchPlugin.PLUGIN_ID, code, message, exception));
	}
	
	protected static void abort(String message) throws CoreException {
		abort(message, null, 0);
	}
}
