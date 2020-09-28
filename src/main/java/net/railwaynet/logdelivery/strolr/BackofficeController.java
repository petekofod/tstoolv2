package net.railwaynet.logdelivery.strolr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
public class BackofficeController {

    private static final Logger logger = LoggerFactory.getLogger(FederationsController.class);

    @Autowired
    private Environment env;

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final String BACKOFFICE_FILE = "backoffice.json";

    public Map<String, List<Map<String, Object>>> getBackofficeData() {
        try {
            return objectMapper.readValue(new FileReader(BACKOFFICE_FILE),
                    new TypeReference<Map<String, List<Map<String, Object>>>>() {});
        } catch (IOException e) {
            logger.error("Can't read " + BACKOFFICE_FILE, e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Can't read the backoffice servers configuration!", e);
        }
    }

    private String getServerStatus(String ip, String key, String username, String script) throws InterruptedException, IOException {
        Runtime rt = Runtime.getRuntime();
        String[] command = { "ssh", "-i", key, username + "@" + ip, script };

        logger.debug("Running remote script as:");
        logger.debug(Arrays.toString(command));

        Process proc;
        try {
            proc = rt.exec(command);
        } catch (IOException e) {
            logger.error("Can't execute the command line:");
            logger.error(Arrays.toString(command));
            throw e;
        }

        BufferedReader stdInput = new BufferedReader(new
                InputStreamReader(proc.getInputStream()));

        StringBuilder res = new StringBuilder();

        String s;
        while (true) {
            try {
                if ((s = stdInput.readLine()) == null) break;
            } catch (IOException e) {
                logger.error("Can't read the command line output!");
                throw e;
            }
            res.append(s).append(System.getProperty("line.separator"));
        }

        try {
            if (proc.waitFor() != 0) {
                logger.error("Can't execute the command line:");
                logger.error(Arrays.toString(command));
                throw new RuntimeException("Invalid exit code of the command line: " + proc.exitValue());
            }
        } catch (InterruptedException e) {
            logger.error("Shell command process failed to terminate!");
            throw e;
        }

        logger.debug("Command output: ");
        logger.debug(res.toString());

        return res.toString();
    }

    private void updateStatus(Map<String, Object> serverFamily) {
        String type = (String) serverFamily.get("type");

        String username;
        String key;
        String script;

        boolean isAWS = type.equals("AWS");

        if (isAWS) {
            username = env.getProperty("backoffice.username");
            key = env.getProperty("backoffice.key_file");
            script = env.getProperty("backoffice.remote_script");
        } else {
            username = env.getProperty("backoffice.aws_username");
            key = env.getProperty("backoffice.aws_key_file");
            script = env.getProperty("backoffice.aws_remote_script");
        }

        @SuppressWarnings("unchecked") List<Map<String, String>> servers = (List<Map<String, String>>) serverFamily.get("servers");

        for (Map<String, String> server: servers) {
            String ip = server.get("IP");
            String status = "";

            try {
                String out = getServerStatus(ip, key, username, script);

                if (isAWS) {
                    if (out.contains("Bad")) status = "CRITICAL";
                    if (out.contains("Good")) status = "OK";
                } else {
                    if (out.contains("CRITICAL")) status = "CRITICAL";
                    if (out.contains("WARNING")) status = "WARNING";
                    if (out.contains("OK")) status = "OK";
                }
            } catch (InterruptedException | IOException e) {
                logger.error("Can't get status of server " + ip, e);
                status = "CRITICAL";
            }

            server.put("status", status);
        }
    }

    @CrossOrigin
    @RequestMapping("/backoffice.json/{scac}")
    public String backoffice(@PathVariable(value = "scac") String scac) {
        scac = scac.toUpperCase();
        logger.debug("Requesting backoffice statuses for " + scac);

        Map<String, List<Map<String, Object>>> config = getBackofficeData();
        if (!config.containsKey(scac)) {
            logger.error("No data for the SCAC " + scac);
            throw new ResponseStatusException(
                    HttpStatus.NO_CONTENT, "No data for the SCAC " + scac);
        }
        Map<String, List<Map<String, Object>>> res = new HashMap<>();
        res.put(scac, config.get(scac));

        for (Map<String, Object> serverFamily: res.get(scac)) {
            updateStatus(serverFamily);
        }

        try {
            return objectMapper.writeValueAsString(res);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Can't serialize the result to JSON!", e);
        }
    }
}