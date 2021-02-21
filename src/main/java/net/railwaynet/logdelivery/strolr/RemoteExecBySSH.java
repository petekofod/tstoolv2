package net.railwaynet.logdelivery.strolr;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class RemoteExecBySSH {

    private static final Logger logger = LoggerFactory.getLogger(RemoteExecBySSH.class);

    public static String execScript(String ip, String key, String username, String script) throws JSchException, IOException {
        logger.debug("Executing script \n" + script);
        logger.debug("at " + ip + ", username is " + username + ", public key is " + key);

        String result;

        JSch jsch = new JSch();
        jsch.addIdentity(key);

        Session session = jsch.getSession(username, ip, 22);
        logger.debug("Session created");

        java.util.Properties config = new java.util.Properties();
        config.put("StrictHostKeyChecking", "no");
        session.setConfig(config);

        try {
            session.connect();
            logger.debug("Session connected");

            ChannelExec channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(script);
            channel.setErrStream(System.err);
            logger.debug("Channel opened, reading output stream");

            InputStream is = channel.getInputStream();
            try {
                channel.connect();
                InputStreamReader isr = new InputStreamReader(is);
                BufferedReader br = new BufferedReader(isr);
                String line;
                StringBuilder sb = new StringBuilder();

                while ((line = br.readLine()) != null)
                    sb.append(line).append(System.lineSeparator());

                logger.debug("Reading completed");
                result = sb.toString();
                logger.debug("Result is \n" + result);
            } finally {
                session.disconnect();
            }
        } finally {
            session.disconnect();
        }

        return result;
    }

}
