package fr.edf.jenkins.plugins.mac.ssh

import java.util.concurrent.TimeUnit
import java.util.logging.Level
import java.util.logging.Logger

import org.apache.commons.lang.RandomStringUtils
import org.apache.commons.lang.StringUtils
import org.kohsuke.accmod.Restricted
import org.kohsuke.accmod.restrictions.NoExternalUse

import com.trilead.ssh2.Connection

import fr.edf.jenkins.plugins.mac.MacHost
import fr.edf.jenkins.plugins.mac.MacUser
import fr.edf.jenkins.plugins.mac.ssh.connection.SSHConnectionConfiguration
import fr.edf.jenkins.plugins.mac.ssh.connection.SSHGlobalConnectionConfiguration
import fr.edf.jenkins.plugins.mac.ssh.connection.SSHUserConnectionConfiguration
import fr.edf.jenkins.plugins.mac.util.Constants
import hudson.util.Secret
import jenkins.model.Jenkins

/**
 * Contains all availables methods to execute ssh command for the Mac plugin
 * @author Mathieu DELROCQ
 *
 */
class SSHCommand {

    static final Logger LOGGER = Logger.getLogger(SSHCommand.name)

    /**
     * Check the given connection to ssh with the command whoami
     * @param connection
     * @return the user connected, or null if error
     */
    @Restricted(NoExternalUse)
    static String checkConnection(SSHGlobalConnectionConfiguration config) {
        return SSHCommandLauncher.executeCommand(config, false,  Constants.WHOAMI)
    }

    /**
     * Create an user with the command sysadminctl
     * @param macHost
     * @return a MacUser
     */
    @Restricted(NoExternalUse)
    static MacUser createUserOnMac(MacHost macHost, MacUser user) throws Exception {
        try {
            SSHGlobalConnectionConfiguration connectionConfig = new SSHGlobalConnectionConfiguration(credentialsId: macHost.credentialsId, port: macHost.port,
            context: Jenkins.get(), host: macHost.host, connectionTimeout: macHost.connectionTimeout,
            readTimeout: macHost.readTimeout, kexTimeout: macHost.kexTimeout)
            LOGGER.log(Level.FINE, SSHCommandLauncher.executeCommand(connectionConfig, true, String.format(Constants.CREATE_USER, user.username, user.password.getPlainText())))
            TimeUnit.SECONDS.sleep(5)
            if(!isUserExist(connectionConfig, user.username)) {
                throw new Exception(String.format("The user %s wasn't created after verification", user.username))
            }
            LOGGER.log(Level.FINE, SSHCommandLauncher.executeCommand(connectionConfig, true, String.format(Constants.CHANGE_RIGHTS_ON_USER, user.username)))
            LOGGER.log(Level.FINE, "The User {0} has been CREATED on Mac {1}", user.username, macHost.host)
            return user
        } catch(Exception e) {
            final String message = String.format(SSHCommandException.CREATE_MAC_USER_ERROR_MESSAGE, macHost.host)
            LOGGER.log(Level.SEVERE, message, e)
            throw new SSHCommandException(message, e)
        }
    }

    /**
     * Delete the given user in parameter
     * @param cloudId
     * @param username
     * @return true if user is deleted, false if an error occured
     */
    @Restricted(NoExternalUse)
    static void deleteUserOnMac(String username, MacHost macHost) throws Exception {
        try {
            SSHGlobalConnectionConfiguration connectionConfig = new SSHGlobalConnectionConfiguration(credentialsId: macHost.credentialsId, port: macHost.port,
            context: Jenkins.get(), host: macHost.host, connectionTimeout: macHost.connectionTimeout,
            readTimeout: macHost.readTimeout, kexTimeout: macHost.kexTimeout)
            LOGGER.log(Level.FINE, SSHCommandLauncher.executeCommand(connectionConfig, true, String.format(Constants.DELETE_USER, username)))
            TimeUnit.SECONDS.sleep(5)
            if(isUserExist(connectionConfig, username)) {
                throw new Exception(String.format("The user %s still exist after verification", username))
            }
            LOGGER.log(Level.FINE, "The User {0} has been DELETED from Mac {1}", username, macHost.host)
        } catch (Exception e) {
            final String message = String.format(SSHCommandException.DELETE_MAC_USER_ERROR_MESSAGE, username, macHost.host)
            LOGGER.log(Level.SEVERE, message, e)
            throw new SSHCommandException(message, e)
        }
    }

    /**
     * Get the slave.jar on Jenkins and connect the slave to JNLP
     * @param macHost
     * @param user
     * @param jnlpConnector
     * @param slaveSecret
     * @return true if connection succeed, false otherwise
     */
    @Restricted(NoExternalUse)
    static boolean jnlpConnect(MacHost macHost, MacUser user, String jenkinsUrl, String slaveSecret) throws SSHCommandException {
        jenkinsUrl = StringUtils.isNotEmpty(jenkinsUrl) ? jenkinsUrl : Jenkins.get().getRootUrl()
        if(!jenkinsUrl.endsWith("/")) {
            jenkinsUrl += "/"
        }
        String remotingUrl = jenkinsUrl + Constants.REMOTING_JAR_PATH
        try {
            SSHUserConnectionConfiguration connectionConfig = new SSHUserConnectionConfiguration(username: user.username, password: user.password, host: macHost.host,
                    port: macHost.port, connectionTimeout: macHost.connectionTimeout, readTimeout: macHost.readTimeout, kexTimeout: macHost.kexTimeout)
            LOGGER.log(Level.FINE, SSHCommandLauncher.executeCommand(connectionConfig, false, String.format(Constants.GET_REMOTING_JAR, remotingUrl)))
            LOGGER.log(Level.FINE, SSHCommandLauncher.executeCommand(connectionConfig, false, String.format(Constants.LAUNCH_JNLP, jenkinsUrl, user.username, slaveSecret)))
            return true
        } catch(Exception e) {
            final String message = String.format(SSHCommandException.JNLP_CONNECTION_ERROR_MESSAGE, macHost.host, user.username)
            LOGGER.log(Level.SEVERE, message, e)
            throw new SSHCommandException(message, e)
        }
    }

    /**
     * Generate a Mac user with the pattern in Constants
     * @return a MacUser
     */
    @Restricted(NoExternalUse)
    static MacUser generateUser() throws Exception {
        String password = RandomStringUtils.random(10, true, true);
        String username = String.format(Constants.USERNAME_PATTERN, RandomStringUtils.random(10, true, true).toLowerCase())
        String workdir = String.format(Constants.WORKDIR_PATTERN, username)
        return new MacUser(username:username, password:Secret.fromString(password), workdir:workdir)
    }

    /**
     * Verify if an user exist on the Mac
     * @param connection
     * @param username
     * @return true if exist, false if not
     */
    @Restricted(NoExternalUse)
    private static boolean isUserExist(SSHConnectionConfiguration connectionConfig, String username) throws Exception {
        String result = SSHCommandLauncher.executeCommand(connectionConfig, true, String.format(Constants.CHECK_USER_EXIST, username))
        return result.trim() == username
    }

    /**
     * List all users on a mac host for a label
     * @param macHost
     * @param label
     * @return true if exist, false if not
     */
    @Restricted(NoExternalUse)
    static List<String> listUsers(MacHost macHost) throws SSHCommandException {
        try {
            SSHGlobalConnectionConfiguration connectionConfig = new SSHGlobalConnectionConfiguration(credentialsId: macHost.credentialsId, port: macHost.port,
            context: Jenkins.get(), host: macHost.host, connectionTimeout: macHost.connectionTimeout,
            readTimeout: macHost.readTimeout, kexTimeout: macHost.kexTimeout)
            String result = SSHCommandLauncher.executeCommand(connectionConfig, true, String.format(Constants.LIST_USERS, Constants.USERNAME_PATTERN.substring(0, Constants.USERNAME_PATTERN.lastIndexOf("%"))))
            LOGGER.log(Level.FINE, result)
            if(StringUtils.isEmpty(result)) return new ArrayList()
            return result.split(Constants.REGEX_NEW_LINE) as List
        } catch(Exception e) {
            String message = String.format(SSHCommandException.LIST_USERS_ERROR_MESSAGE, macHost.host, e.getMessage())
            LOGGER.log(Level.SEVERE, message, e)
            throw new SSHCommandException(message, e)
        }
    }
}