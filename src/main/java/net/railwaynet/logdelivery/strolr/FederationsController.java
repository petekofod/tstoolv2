package net.railwaynet.logdelivery.strolr;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.JSchException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.FileReader;
import java.io.IOException;
import java.util.*;

@RestController
public class FederationsController {

    private static final Logger logger = LoggerFactory.getLogger(FederationsController.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${scac.federations.mapping.file:scac.json}")
    private String SCAC_FILE;

    private static Map<String, List<String>> SCAC_FEDERATION;

    @Autowired
    private Environment env;

    private static String IOB_ADDRESS;
    private static String REMOTE_HOST;
    private static String REMOTE_USERNAME;
    private static String REMOTE_KEY;

    public Map<String, List<String>> getScacFederation() {
        if (SCAC_FEDERATION == null) {
            try {
                SCAC_FEDERATION = objectMapper.readValue(new FileReader(SCAC_FILE),
                        new TypeReference<Map<String, List<String>>>() {});
                if (logger.isInfoEnabled()) {
                    logger.info("List of SCAC:");
                    for (Map.Entry<String, List<String>> entry: SCAC_FEDERATION.entrySet()) {
                        logger.info(entry.getKey() + " : " + Arrays.toString(entry.getValue().toArray()));
                    }
                }
            } catch (IOException e) {
                logger.error("Can't read " + SCAC_FILE, e);
                throw new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Can't read the list of SCAC/railroads!", e);
            }
        }

        return SCAC_FEDERATION;
    }

    public String getRemoteHost() {
        if (REMOTE_HOST == null) {
            REMOTE_HOST = env.getProperty("remote_host");
            logger.info("SSH remote host: " + REMOTE_HOST);
        }

        return REMOTE_HOST;
    }

    public String getRemoteUsername() {
        if (REMOTE_USERNAME == null) {
            REMOTE_USERNAME = env.getProperty("remote_username");
            logger.info("SSH username: " + REMOTE_USERNAME);
        }

        return REMOTE_USERNAME;
    }

    public String getRemoteKey() {
        if (REMOTE_KEY == null) {
            REMOTE_KEY = env.getProperty("remote_key");
            logger.info("SSH key: " + REMOTE_KEY);
        }

        return REMOTE_KEY;
    }

    private String getIP() {
        if (IOB_ADDRESS == null) {
            IOB_ADDRESS = env.getProperty("iobaddress");
            logger.info("IOB Address: " + IOB_ADDRESS);
        }

        return IOB_ADDRESS;
    }

    private Map<String, Object> handleFederation(String name, String[] outputLines) {
        logger.debug("Parsing...");

        Map<String, Object> json = new HashMap<>();
        json.put("name", name);

        boolean headerPassed = false;
        int totalHosts = 0;
        int operationalHosts = 0;
        for (String line: outputLines) {
            logger.debug("next line: " + line);
            if (headerPassed) {
                String[] tokens = line.split("\\s+");
                String host = tokens[0];
                String status = tokens[4];
                logger.debug("Host: " + host + ", status: " + status);

                if (host.startsWith(name)) {
                    totalHosts++;
                    if (status.equals("Operational"))
                        operationalHosts++;
                }
            } else {
                headerPassed = line.contains("================");
            }
        }

        json.put("total_servers", totalHosts);
        json.put("operational_servers", operationalHosts);

        return json;
    }

    @CrossOrigin
    @RequestMapping("/federations.json/{scac}")
    public String railroads(@PathVariable(value="scac") String scac) {
        scac = scac.toUpperCase();
        logger.debug("Requesting federation statuses for " + scac);

        List<String> federations = getScacFederation().get(scac);

        if (federations == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Unknown SCAC!");
        }

        String qpidRoute;
        try {
            qpidRoute = RemoteExecBySSH.execScript(getRemoteHost(), getRemoteKey(), getRemoteUsername(), "qpid-route link list " + getIP() + ":16000");
        } catch (JSchException | IOException e) {
            logger.debug("Can't get results of qpid-route!", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Can't get results of qpid-route command!", e);
        }

        String[] outputLines = qpidRoute.split(System.getProperty("line.separator"));

        Map<String,Object> json = new HashMap<>();
        json.put("SCAC", scac);
        List<Map<String, Object>> federationsJson = new ArrayList<>();
        json.put("federations", federationsJson);

        for (String f: federations) {
            logger.debug("Adding federation " + f);
            federationsJson.add(handleFederation(f, outputLines));
        }

        try {
            return objectMapper.writeValueAsString(json);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Can't serialize the result to JSON!", e);
        }
    }

}
