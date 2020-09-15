package net.railwaynet.logdelivery.strolr;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

@RestController
public class FederationsController {

    private static final Logger logger = LoggerFactory.getLogger(FederationsController.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private static final Map<String, List<String>> SCAC_FEDERATION = new HashMap<>();

    static {
        SCAC_FEDERATION.put("AMTK", Arrays.asList("amtk", "bnsf", "cn", "cprs", "csao", "csxt", "htix", "kcs", "metx", "nctc", "njtr", "ns", "pjpb", "rcax", "scax", "sepa", "up", "xorr"));
        SCAC_FEDERATION.put("VREX", Collections.singletonList("vrex"));
    }

    @Autowired
    private Environment env;

    private static String IOB_ADDRESS;
    private static String REMOTE_HOST;
    private static String REMOTE_USERNAME;
    private static String REMOTE_KEY;

    public String getRemoteHost() {
        if (REMOTE_HOST == null) {
            REMOTE_HOST = env.getProperty("remote_host");
        }

        return REMOTE_HOST;
    }

    public String getRemoteUsername() {
        if (REMOTE_USERNAME == null) {
            REMOTE_USERNAME = env.getProperty("remote_username");
        }

        return REMOTE_USERNAME;
    }

    public String getRemoteKey() {
        if (REMOTE_KEY == null) {
            REMOTE_KEY = env.getProperty("remote_key");
        }

        return REMOTE_KEY;
    }

    private String getIP() {
        if (IOB_ADDRESS == null) {
            IOB_ADDRESS = env.getProperty("iobaddress");
        }

        return IOB_ADDRESS;
    }

    private String callQpidRoute(String ip, String key, String username, String host) throws IOException {
        Runtime rt = Runtime.getRuntime();
        String[] command = { "ssh", "-i", key, username + "@" + host,
                "qpid-route", "link", "list", ip + ":16000"};

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

        logger.debug("qpid-route output: ");
        logger.debug(res.toString());

        return res.toString();
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
        logger.debug("Requesting federation statuses for " + scac);

        List<String> federations = SCAC_FEDERATION.get(scac);

        if (federations == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Unknown SCAC!");
        }

        String qpidRoute;
        try {
            qpidRoute = callQpidRoute(getIP(), getRemoteKey(), getRemoteUsername(), getRemoteHost());
        } catch (IOException e) {
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
