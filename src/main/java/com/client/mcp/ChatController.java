package com.client.mcp;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;

@RestController
@CrossOrigin(origins = "http://localhost:3000")
public class ChatController {

	Logger log = LogManager.getLogger(ChatController.class);
	private final ChatClient chatClient;

	public ChatController(ChatClient.Builder builder, ToolCallbackProvider tools) {

		Arrays.stream(tools.getToolCallbacks())
				.forEach(t -> log.info("Tool Callback found: {}", t.getToolDefinition()));

		this.chatClient = builder.defaultToolCallbacks(tools).build();
	}

	@GetMapping("/chat")
    public String chat(@RequestParam String message) {

        var response = chatClient.prompt()
            .system(
            		"""
You are an assistant connected to a Splunk MCP server.

You MUST use MCP tools whenever the user asks about:
- logs, events, errors, warnings
- retries, failures, or exceptions
- searching or querying Splunk
- indexes or sending data to Splunk

Available tools:
- splunk_list_indexes
- run_splunk_query
- splunk_send_event

CRITICAL RULES (DO NOT VIOLATE):
- If a Splunk tool returns events, you MUST assume those events are real and valid.
- You MUST count events based on the number of results returned by the tool.
- Do NOT conclude "no events found" if the tool output contains any events.
- Do NOT reinterpret or discard results due to field nesting or structure.
- Trust tool output over your own assumptions.

Log structure guidance:
- Log severity may appear as `event.severity`
- Log message may appear as `event.message`
- Logger may appear as `event.logger`
- These fields may NOT be at the top level

After receiving tool output:
- First, determine whether any events were returned.
- If events exist, ALWAYS include the following sections:

Summary:
- Briefly describe what was observed.

Details:
- Number of events (based on count of returned results)
- Time range of the events
- Key components or loggers involved
- Most common error or warning message

Interpretation:
- Explain what the pattern indicates, based ONLY on the returned data.

Formatting rules:
- Group similar events together (do not list every event)
- Do NOT include raw JSON unless explicitly requested
- Do NOT fabricate values, IDs, or counts

If (and ONLY if) the tool returns zero events:
- Clearly state that no events matched the criteria.


""")
            .user(message)
            .call()
            .content();
        
        return response;
    }

}
// package com.client.mcp;

// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;
// import org.springframework.ai.chat.client.ChatClient;
// import org.springframework.ai.tool.ToolCallbackProvider;
// import org.springframework.web.bind.annotation.*;
// import org.springframework.web.reactive.function.client.WebClient;

// import java.util.*;

// @RestController
// @CrossOrigin(origins = "http://localhost:3000")
// public class ChatController {

//     private static final Logger log = LoggerFactory.getLogger(ChatController.class);

//     private final ChatClient chatClient;
//     private final WebClient serverClient;

//     public ChatController(ChatClient.Builder builder, ToolCallbackProvider tools) {

//         // -------------------------------------------------------------------
//         // KEEP THIS — LOG ALL REGISTERED TOOL CALLBACKS
//         // -------------------------------------------------------------------
//         Arrays.stream(tools.getToolCallbacks()).forEach(t ->
//                 log.info("Tool Callback found: {}", t.getToolDefinition())
//         );

//         this.chatClient = builder
//                 .defaultToolCallbacks(tools)
//                 .build();

//         // MCP server REST endpoints (your tool server)
//         this.serverClient = WebClient.builder()
//                 .baseUrl("http://localhost:8080/mcp")   // adjust if needed
//                 .build();
//     }

//     // ======================================================================
//     // MAIN ENDPOINT — NATURAL LANGUAGE → TOOL SELECTION → SERVER CALL
//     // ======================================================================
//     @GetMapping("/chat")
//     public String chat(@RequestParam String message) {

//         log.info("Received NL query: {}", message);

//         ParsedIntent intent = interpret(message);

//         // If no tool detected → fall back to normal LLM chat
//         if (intent.tool == null) {
//             log.info("No tool match. Forwarding to LLM.");
//             return chatClient.prompt().user(message).call().content();
//         }

//         // Log tool selection + extracted parameters
//         log.info("Selected tool: {}", intent.tool);
//         log.info("Extracted params: {}", intent.params);

//         // -------------------------------------------------------------------
//         // MANUALLY CALL THE SERVER TOOL ENDPOINTS
//         // -------------------------------------------------------------------
//         switch (intent.tool) {

//             case "splunk_list_indexes":
//                 return serverClient.get()
//                         .uri("/tools/splunk/indexes")
//                         .retrieve()
//                         .bodyToMono(String.class)
//                         .block();

//             case "splunk_search":
//             	return serverClient.get()
//             	        .uri(uriBuilder ->
//             	                uriBuilder
//             	                        .path("/tools/splunk/search")
//             	                        .queryParam("query", intent.params.get("query"))
//             	                        .build()
//             	        )
//             	        .retrieve()
//             	        .bodyToMono(String.class)
//             	        .block();


//             case "splunk_get_search_results":
//                 return serverClient.get()
//                         .uri("/tools/splunk/results?sid=" + intent.params.get("sid"))
//                         .retrieve()
//                         .bodyToMono(String.class)
//                         .block();

//             case "splunk_send_event":
//                 return serverClient.post()
//                         .uri("/tools/splunk/event")
//                         .bodyValue(intent.params)
//                         .retrieve()
//                         .bodyToMono(String.class)
//                         .block();

//             default:
//                 log.warn("Unhandled tool: {}", intent.tool);
//                 return "Unknown tool.";
//         }
//     }

//     // ======================================================================
//     // LIGHTWEIGHT NLP → TOOL + PARAMETER EXTRACTION
//     // ======================================================================

//     private record ParsedIntent(String tool, Map<String, Object> params) {}

//     private ParsedIntent interpret(String msg) {
//         List<String> words = Arrays.asList(msg.toLowerCase().split("\\s+"));

//         // Tool: list indexes
//         if (words.contains("list") || words.contains("indexes")) {
//             return new ParsedIntent("splunk_list_indexes", Map.of());
//         }

//         // Tool: create search job
//      // Tool: create search job
//      // Tool: create search job
//         if (words.contains("search")) {

//             String index = "main";  // default fallback
//             for (int i = 0; i < words.size(); i++) {
//                 if (words.get(i).equals("in") && i + 1 < words.size()) {
//                     index = words.get(i + 1);   // captures "springboot_api_dev"
//                     break;
//                 }
//             }

//             // Extract time expression
//             String earliest = "-24h"; // default
//             if (msg.toLowerCase().contains("last 24 hours")) {
//                 earliest = "-24h";
//             }

//             String splunkQuery = String.format(
//                     "search index=%s earliest=%s",
//                     index,
//                     earliest
//             );

//             return new ParsedIntent("splunk_search", Map.of("query", splunkQuery));
//         }




//         // Tool: get search results
//         if (words.contains("results") || words.stream().anyMatch(w -> w.startsWith("sid="))) {
//             return new ParsedIntent("splunk_get_search_results",
//                     Map.of("sid", extractSid(words)));
//         }

//         // Tool: send event to HEC
//         if (words.contains("send") && words.contains("event")) {
//             String index = extractAfter(words, "index");
//             String event = extractAfter(words, "event");
//             return new ParsedIntent("splunk_send_event",
//                     Map.of("index", index, "event", event));
//         }

//         return new ParsedIntent(null, Map.of());
//     }

//     private String extractSid(List<String> words) {
//         for (int i = 0; i < words.size(); i++) {
//             if (words.get(i).equals("sid") && i + 1 < words.size()) {
//                 return words.get(i + 1); // next word is the SID
//             }
//             if (words.get(i).startsWith("sid=")) {
//                 return words.get(i).substring(4);
//             }
//         }
//         return "";
//     }


//     private String extractAfter(List<String> words, String key) {
//         for (int i = 0; i < words.size(); i++) {
//             if (words.get(i).equals(key) && i + 1 < words.size()) {
//                 return words.get(i + 1);
//             }
//         }
//         return "";
//     }
// }
//package com.client.mcp;
//
//import org.apache.logging.log4j.LogManager;
//import org.apache.logging.log4j.Logger;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.ai.tool.ToolCallbackProvider;
//import org.springframework.web.bind.annotation.*;
//
//import java.util.Arrays;
//
//@RestController
//public class ChatController {
//
//    Logger log = LogManager.getLogger(ChatController.class);
//    private final ChatClient chatClient;
//
//    public ChatController(ChatClient.Builder builder, ToolCallbackProvider tools) {
//
//        Arrays.stream(tools.getToolCallbacks()).forEach(t ->
//                log.info("Tool Callback found: {}", t.getToolDefinition())
//        );
//
//        this.chatClient = builder
//                .defaultToolCallbacks(tools)
//                .build();
//    }
//
//    @GetMapping("/chat")
//    public String chat(@RequestParam String message) {
//
//        var response = chatClient.prompt()
//            .system("""
//    You are an assistant connected to a Splunk MCP server.
//
//    You MUST use tools when the user asks about:
//    - indexes
//    - logs
//    - events
//    - searching Splunk
//    - sending data to Splunk
//
//    Available tools:
//    - splunk_list_indexes
//    - splunk_search
//    - splunk_get_search_results
//    - splunk_send_event
//
//    If a tool is relevant, CALL IT.
//    Do NOT answer from your own knowledge.
//    After receiving tool output, repeat it EXACTLY.
//    """)
//            .user(message)
//            .call()
//            .content();
//        
//        return response;
//    }
//
//
//}
