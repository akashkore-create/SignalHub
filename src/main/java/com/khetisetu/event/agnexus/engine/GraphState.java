package com.khetisetu.event.agnexus.engine;

/**
 * Represents the current state of an agent graph execution.
 * Used for tracking execution flow and enabling conditional transitions.
 */
public enum GraphState {
    /** Initial state — query is being analyzed for routing */
    ROUTING,
    /** An agent is actively processing the query */
    PROCESSING,
    /** Agent is calling external tools (weather, mandi prices, etc.) */
    TOOL_CALLING,
    /** Synthesizing final response from tool/agent results */
    SYNTHESIZING,
    /** Execution completed successfully */
    COMPLETE,
    /** An error occurred during execution */
    ERROR
}
