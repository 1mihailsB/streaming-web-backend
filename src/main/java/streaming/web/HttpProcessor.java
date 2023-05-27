package streaming.web;

import java.util.HashMap;
import java.util.Map;

public class HttpProcessor {
    static Logger logger;
    final static String CRLF = "\r\n";

    static {
        logger = new Logger(HttpProcessor.class.getName());
    }

    public static String process(byte[] data) {
        var req = new String(data);
        logger.info((String.format(
                "Data from client:\n----------------------------------------\n%s",
                req
        )));

        String[] split = req.split(CRLF + CRLF);
        System.out.println("Len:" + split.length);
        System.out.println("Req\n" + req);

        boolean hasBody = split.length == 2;
        String body = hasBody ? split[1] : null;

        var beforeBody = split[0].split(CRLF, 2);
        String[] requestLine = beforeBody[0].split(" "); // GET / HTTP/1.1
        var method = requestLine[0];
        var path = requestLine[1];
        var httpVer = requestLine[2];

        String headerData = beforeBody[1];

        Map<String, String> headers = new HashMap<>();
        for (var h : headerData.split(CRLF)) {
            var header = h.split(":");
            var name = header[0];
            var val = header[1].trim();

            headers.put(name, val);
        }

        System.out.println("header map: " + headers);
        return getResponse();
    }

    private static String getResponse() {
        String html = "{\"data\": \"custom html response\"}";
        final String CRLF = "\r\n"; // 13, 10
        String response =
                "HTTP/1.1 200 OK" + CRLF
                        + "Access-Control-Allow-Origin: *" + CRLF
                        + "Content-Length: " + (html.getBytes().length + CRLF.getBytes().length) + CRLF
                        + CRLF
                        + html
                        + CRLF + CRLF;

        return response;
    }
}
