package streaming.web;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Use 1 per request. Inside virtual thread for example.
 */
public class HttpProcessor {
    static Logger logger;
    final static String CRLF = "\r\n";

    static {
        logger = new Logger(HttpProcessor.class.getName());
    }

    private SocketChannel chan;

    public HttpProcessor(SocketChannel chan) {
        this.chan = chan;
    }

    private enum RequestAssemblyStatus {
        NEW,
        HEADER,
        BODY;
    }

    private RequestAssemblyStatus assemblyStatus = RequestAssemblyStatus.NEW;

    public enum RequestStatus {
        PROCESSING,
        DONE;
    }

    private RequestStatus status = RequestStatus.PROCESSING;

    private String method;
    private String protocol;
    private String path;

    private StringBuilder headersData = new StringBuilder();
    Map<String, String> headers = new HashMap<>();
    private StringBuilder bodyData = new StringBuilder();
    private int bodyLen = 0;

    public RequestStatus acceptRequestChunk(byte[] data) throws IOException {
        var req = new String(data);

        if (this.assemblyStatus == RequestAssemblyStatus.NEW) {
            var firstCrlf = req.indexOf(CRLF);
            var requestLine = req.substring(0, firstCrlf);
            var components = requestLine.split(" ");

            this.method = components[0];
            this.path = components[1];
            this.protocol = components[2];

            req = req.substring(firstCrlf);
            this.assemblyStatus = RequestAssemblyStatus.HEADER;
        }

        if (this.assemblyStatus == RequestAssemblyStatus.HEADER) {
            var endOfHeaders = req.indexOf(CRLF + CRLF);

            if (endOfHeaders != -1) {
                var split = req.split(CRLF + CRLF);
                headersData.append(split[0]);
                this.parseHeaders();

                if (headers.containsKey("Content-Length")) {
                    if (split.length == 2) {
                        // first body chunk
                        bodyData.append(split[1]);
                        bodyLen += split[1].getBytes().length;
                        assemblyStatus = RequestAssemblyStatus.BODY;
                    }

                    if (Integer.parseInt(headers.get("Content-Length")) == bodyLen) {
                        return status = RequestStatus.DONE;
                    }

                    return RequestStatus.PROCESSING;
                }

                return status = RequestStatus.DONE;
            }

            headersData.append(req);

            return RequestStatus.PROCESSING;
        }

        if (this.assemblyStatus == RequestAssemblyStatus.BODY) {
            var cl = Integer.parseInt(headers.get("Content-Length"));
            var dl = req.getBytes().length;
            if (dl + bodyLen > cl) {
                bodyData.append(Arrays.copyOfRange(req.getBytes(), 0, cl - bodyLen));
                bodyLen += cl - bodyLen;

                return status = RequestStatus.DONE;
            }

            bodyData.append(req);
            bodyLen += req.getBytes().length;

            if (bodyLen == cl) {
                return status = RequestStatus.DONE;
            }

            return RequestStatus.PROCESSING;
        }

        return RequestStatus.PROCESSING;
    }

    public String getResponse() {
        if (this.status != RequestStatus.DONE) {
            return null;
        }

        String html = bodyData.toString();
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

    private void parseHeaders() {
        for (var h : headersData.toString().trim().split(CRLF)) {
            var header = h.split(":");
            var name = header[0];
            var val = header[1].trim();

            headers.put(name, val);
        }
    }
}
