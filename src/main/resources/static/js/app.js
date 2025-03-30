/**
 * Plan Executor Dashboard
 * Handles fetching and displaying the current plan and execution log
 */

// App state
let lastSystemLogId = 0;
let lastConversationLogId = 0;
let currentPlanId = null;
let isExecuting = false;

// DOM elements
const planContent = document.getElementById('plan-content');
const systemContent = document.getElementById('system-content');
const conversationContent = document.getElementById('conversation-content');
const runDefaultButton = document.getElementById('run-default');
const runCustomButton = document.getElementById('run-custom');
const customPromptInput = document.getElementById('custom-prompt');
const fixesContent = document.getElementById('fixes-content');
const toggleFixesButton = document.getElementById('toggle-fixes');
const tabButtons = document.querySelectorAll('.tab-button');
const logPanels = document.querySelectorAll('.log-panel');

// Initialize the application
document.addEventListener('DOMContentLoaded', () => {
    initializeApp();
});

function initializeApp() {
    // Start polling for updates
    fetchCurrentPlanId();
    
    // Poll for updates
    setInterval(() => {
        fetchCurrentPlanId();
        fetchNewSystemLogs();
        fetchNewConversationLogs();
    }, 1000); // Poll every second
    
    // Set up button event listeners
    runDefaultButton.addEventListener('click', executeDefaultPlan);
    runCustomButton.addEventListener('click', executeCustomPlan);
    
    // Handle Enter key in prompt input
    customPromptInput.addEventListener('keyup', (event) => {
        if (event.key === 'Enter') {
            executeCustomPlan();
        }
    });
    
    // Setup tab switching
    tabButtons.forEach(button => {
        button.addEventListener('click', () => {
            // Remove active class from all buttons and panels
            tabButtons.forEach(btn => btn.classList.remove('active'));
            logPanels.forEach(panel => panel.classList.remove('active'));
            
            // Add active class to clicked button and corresponding panel
            button.classList.add('active');
            const tabName = button.getAttribute('data-tab');
            document.getElementById(`${tabName}-panel`).classList.add('active');
        });
    });
    
    // Setup task click handlers (for expanding/collapsing)
    document.addEventListener('click', function(event) {
        if (event.target.closest('.task-header')) {
            const taskHeader = event.target.closest('.task-header');
            const taskContent = taskHeader.nextElementSibling;
            const toggleIcon = taskHeader.querySelector('.toggle-icon');
            
            taskContent.classList.toggle('expanded');
            toggleIcon.classList.toggle('expanded');
        }
    });
    
    // Setup toggle button for fix history
    if (toggleFixesButton) {
        toggleFixesButton.addEventListener('click', toggleFixHistory);
    }
}

/**
 * Execute the default plan
 */
function executeDefaultPlan() {
    if (isExecuting) {
        alert('A plan is already being executed. Please wait for it to complete.');
        return;
    }
    
    isExecuting = true;
    updateButtonState();
    
    fetch('/api/execute', {
        method: 'POST'
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to start execution');
        }
        return response.json();
    })
    .then(data => {
        console.log('Execution started:', data);
        clearLog();
    })
    .catch(error => {
        console.error('Error starting execution:', error);
        isExecuting = false;
        updateButtonState();
    });
}

/**
 * Execute a plan with a custom prompt
 */
function executeCustomPlan() {
    const prompt = customPromptInput.value.trim();
    
    if (!prompt) {
        alert('Please enter a prompt for the plan.');
        return;
    }
    
    if (isExecuting) {
        alert('A plan is already being executed. Please wait for it to complete.');
        return;
    }
    
    isExecuting = true;
    updateButtonState();
    
    fetch(`/api/execute/custom?prompt=${encodeURIComponent(prompt)}`, {
        method: 'POST'
    })
    .then(response => {
        if (!response.ok) {
            throw new Error('Failed to start execution');
        }
        return response.json();
    })
    .then(data => {
        console.log('Custom execution started:', data);
        clearLog();
    })
    .catch(error => {
        console.error('Error starting custom execution:', error);
        isExecuting = false;
        updateButtonState();
    });
}

/**
 * Clear the log displays
 */
function clearLog() {
    systemContent.innerHTML = 'Starting execution...';
    conversationContent.innerHTML = 'Waiting for LLM conversation...';
    lastSystemLogId = 0;
    lastConversationLogId = 0;
}

/**
 * Update button state based on execution status
 */
function updateButtonState() {
    runDefaultButton.disabled = isExecuting;
    runCustomButton.disabled = isExecuting;
    customPromptInput.disabled = isExecuting;
    
    if (isExecuting) {
        runDefaultButton.textContent = 'Executing...';
        runCustomButton.textContent = 'Executing...';
    } else {
        runDefaultButton.textContent = 'Run Default Plan';
        runCustomButton.textContent = 'Run Custom Plan';
    }
}

/**
 * Fetch the current plan ID and update if changed
 */
function fetchCurrentPlanId() {
    fetch('/api/plan/current')
        .then(response => {
            if (!response.ok) {
                if (response.status === 404) {
                    // No current plan ID, just return
                    return null;
                }
                throw new Error('Failed to fetch current plan ID');
            }
            return response.json();
        })
        .then(data => {
            if (data) {
                if (data.planId !== currentPlanId) {
                    currentPlanId = data.planId;
                    fetchCurrentPlan();
                }
            } else {
                planContent.textContent = 'No active plan. Use the buttons above to start execution.';
                currentPlanId = null;
            }
        })
        .catch(error => {
            console.error('Error fetching plan ID:', error);
        });
}

/**
 * Fetch the current plan details
 */
function fetchCurrentPlan() {
    fetch(`/api/plan/${currentPlanId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to fetch plan details');
            }
            return response.json();
        })
        .then(plan => {
            displayPlan(plan);
            // If we received a plan, execution is likely complete or about to start
            checkExecutionStatus();
        })
        .catch(error => {
            console.error('Error fetching plan details:', error);
            planContent.textContent = 'Error loading plan. Please refresh the page.';
        });
}

/**
 * Check if execution is complete based on the log content
 */
function checkExecutionStatus() {
    // If we've received logs that indicate completion, update execution state
    if (systemContent.textContent.includes('Plan execution completed successfully') ||
        conversationContent.textContent.includes('Plan execution completed successfully')) {
        isExecuting = false;
        updateButtonState();
    }
}

/**
 * Display the plan in the UI with task formatting
 */
function displayPlan(plan) {
    // Clear existing content
    planContent.innerHTML = '';
    
    // Create header
    const header = document.createElement('div');
    header.innerHTML = `<h3>Plan ID: ${currentPlanId}</h3>`;
    planContent.appendChild(header);
    
    // Render tasks
    if (plan.topLevelTasks && plan.topLevelTasks.length > 0) {
        renderTasks(plan.topLevelTasks, planContent, 0);
    } else {
        const noTasks = document.createElement('div');
        noTasks.textContent = 'No tasks in the current plan.';
        planContent.appendChild(noTasks);
    }
}

/**
 * Recursively render tasks with proper indentation
 */
function renderTasks(tasks, container, depth) {
    const indent = '  '.repeat(depth);
    
    tasks.forEach(task => {
        const taskEl = document.createElement('div');
        taskEl.className = `task ${getTaskStatusClass(task)}`;
        
        // Create task header (always visible)
        const taskHeader = document.createElement('div');
        taskHeader.className = 'task-header';
        
        // Add indentation and task title
        const taskTitle = document.createElement('span');
        taskTitle.className = 'task-title';
        taskTitle.textContent = `${indent}● Task ${task.id}: ${task.description}`;
        
        // Add expand/collapse icon
        const toggleIcon = document.createElement('span');
        toggleIcon.className = 'toggle-icon';
        toggleIcon.textContent = '►';
        
        // Build header
        taskHeader.appendChild(taskTitle);
        taskHeader.appendChild(toggleIcon);
        taskEl.appendChild(taskHeader);
        
        // Create collapsible content area
        const taskContent = document.createElement('div');
        taskContent.className = 'task-content';
        
        // Add status
        let statusText = '';
        if (task.completed) {
            statusText = `${indent}  Status: Completed`;
        } else if (task.inProgress) {
            statusText = `${indent}  Status: In Progress`;
        } else {
            statusText = `${indent}  Status: Pending`;
        }
        const statusEl = document.createElement('div');
        statusEl.textContent = statusText;
        taskContent.appendChild(statusEl);
        
        // Add commands if any
        if (task.commands && task.commands.length > 0) {
            const commandsTitle = document.createElement('div');
            commandsTitle.textContent = `${indent}  Commands:`;
            taskContent.appendChild(commandsTitle);
            
            const commandsList = document.createElement('ul');
            task.commands.forEach(cmd => {
                const cmdItem = document.createElement('li');
                cmdItem.textContent = cmd;
                commandsList.appendChild(cmdItem);
            });
            taskContent.appendChild(commandsList);
        }
        
        // Add success criteria if available
        if (task.successCriteria) {
            const criteriaEl = document.createElement('div');
            criteriaEl.textContent = `${indent}  Success Criteria: ${task.successCriteria}`;
            taskContent.appendChild(criteriaEl);
        }
        
        taskEl.appendChild(taskContent);
        container.appendChild(taskEl);
        
        // Render subtasks if any
        if (task.subTasks && task.subTasks.length > 0) {
            renderTasks(task.subTasks, container, depth + 1);
        }
    });
}

/**
 * Get CSS class based on task status
 */
function getTaskStatusClass(task) {
    if (task.completed) {
        return 'completed';
    } else if (task.inProgress) {
        return 'in-progress';
    }
    return '';
}

/**
 * Fetch new system log entries since the last check
 */
function fetchNewSystemLogs() {
    fetch(`/api/logs/system?since=${lastSystemLogId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to fetch system log entries');
            }
            return response.json();
        })
        .then(entries => {
            if (entries.length > 0) {
                appendSystemLogEntries(entries);
                lastSystemLogId = entries[entries.length - 1].id;
                
                // Check for execution status changes
                if (entries.some(entry => entry.message.includes('Plan execution completed successfully'))) {
                    isExecuting = false;
                    updateButtonState();
                }
            }
        })
        .catch(error => {
            console.error('Error fetching system logs:', error);
        });
}

/**
 * Fetch new conversation log entries since the last check
 */
function fetchNewConversationLogs() {
    fetch(`/api/logs/conversation?since=${lastConversationLogId}`)
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to fetch conversation log entries');
            }
            return response.json();
        })
        .then(entries => {
            if (entries.length > 0) {
                appendConversationLogEntries(entries);
                lastConversationLogId = entries[entries.length - 1].id;
            }
        })
        .catch(error => {
            console.error('Error fetching conversation logs:', error);
        });
}

/**
 * Append new system log entries to the system log display
 */
function appendSystemLogEntries(entries) {
    // If this is the first set of entries, clear the "waiting" message
    if (systemContent.textContent === 'Waiting for system logs...' || 
        systemContent.textContent === 'Starting execution...') {
        systemContent.innerHTML = '';
    }
    
    entries.forEach(entry => {
        const logEntry = document.createElement('div');
        logEntry.className = `log-entry ${getLogEntryClass(entry)}`;
        logEntry.textContent = formatLogEntry(entry);
        systemContent.appendChild(logEntry);
    });
    
    // Scroll to bottom
    systemContent.scrollTop = systemContent.scrollHeight;
}

/**
 * Append new conversation log entries to the conversation log display
 */
function appendConversationLogEntries(entries) {
    // If this is the first set of entries, clear the "waiting" message
    if (conversationContent.textContent === 'Waiting for LLM conversation...') {
        conversationContent.innerHTML = '';
    }
    
    entries.forEach(entry => {
        const logEntry = document.createElement('div');
        logEntry.className = `log-entry ${getLogEntryClass(entry)}`;
        logEntry.textContent = formatLogEntry(entry);
        conversationContent.appendChild(logEntry);
    });
    
    // Scroll to bottom
    conversationContent.scrollTop = conversationContent.scrollHeight;
}

/**
 * Format a log entry for display
 */
function formatLogEntry(entry) {
    const timestamp = new Date(entry.timestamp).toLocaleTimeString();
    
    // Remove Spring Boot timestamp and thread information from the message if present
    let message = entry.message;
    
    // Match and remove patterns like "2025-03-30 21:31:44.713 INFO 29506 --- [ main]"
    message = message.replace(/\d{4}-\d{2}-\d{2}\s\d{2}:\d{2}:\d{2}\.\d{3}\s+\w+\s+\d+\s+---\s+\[\s*[^\]]+\]\s+/, '');
    
    return `[${timestamp}] ${message}`;
}

/**
 * Get the appropriate CSS class for a log entry based on content
 */
function getLogEntryClass(entry) {
    switch (entry.level) {
        case 'INFO':
            return 'log-info';
        case 'ERROR':
            return 'log-error';
        case 'SUCCESS':
            return 'log-success';
        case 'COMMAND':
            return 'log-command';
        case 'WARNING':
            return 'log-warning';
        case 'LLM_REQUEST':
            return 'log-llm-request';
        case 'LLM_RESPONSE':
            return 'log-llm-response';
        default:
            return '';
    }
}

/**
 * Toggle the visibility of the fix history panel
 */
function toggleFixHistory() {
    fixesContent.classList.toggle('hidden');
    if (fixesContent.classList.contains('hidden')) {
        toggleFixesButton.textContent = 'Show Fix History';
    } else {
        toggleFixesButton.textContent = 'Hide Fix History';
        fetchFixLogs();
    }
}

/**
 * Fetch the plan fix logs
 */
function fetchFixLogs() {
    if (!currentPlanId) {
        return;
    }
    
    // First try the specific plan fixes endpoint
    fetch(`/api/plan-fixes/${currentPlanId}`)
        .then(response => {
            if (!response.ok) {
                // Fallback to general fix logs endpoint
                return fetch('/api/logs/fixes')
                    .then(response => {
                        if (!response.ok) {
                            throw new Error('Failed to fetch fix logs');
                        }
                        return response.json();
                    })
                    .then(logs => ({ fixLogs: logs }));
            }
            return response.json();
        })
        .then(data => {
            displayFixLogs(data.fixLogs || []);
        })
        .catch(error => {
            console.error('Error fetching fix logs:', error);
            const fixLogs = fixesContent.querySelector('.fix-logs');
            fixLogs.innerHTML = '<div class="error">Error fetching fix logs. Please try again.</div>';
        });
}

/**
 * Display fix logs in the fix history panel
 */
function displayFixLogs(logs) {
    const fixLogs = fixesContent.querySelector('.fix-logs');
    
    if (logs.length === 0) {
        fixLogs.innerHTML = '<div class="placeholder">No fix history available for this plan.</div>';
        return;
    }
    
    fixLogs.innerHTML = '';
    
    logs.forEach(log => {
        const logEntry = document.createElement('div');
        logEntry.className = `fix-log-entry ${getLogEntryClass(log)}`;
        logEntry.textContent = formatLogEntry(log);
        fixLogs.appendChild(logEntry);
    });
} 