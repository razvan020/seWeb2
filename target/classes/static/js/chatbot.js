document.addEventListener('DOMContentLoaded', function() {
    // Initialize the chatbot
    initChatbot();
});

let sessionId = null;
let currentContext = 'home'; // Default context

function initChatbot() {
    // Create the chat widget structure if it doesn't exist
    const chatWidget = document.getElementById('chat-widget');
    if (!chatWidget) return;

    // Generate a session ID
    sessionId = generateSessionId();

    // Create the chat toggle button if it doesn't exist
    if (!document.querySelector('.chat-toggle')) {
        createChatToggle();
    }

    // Initialize the chat widget structure
    initChatWidgetStructure(chatWidget);

    // Load conversation starters based on current context
    loadConversationStarters(currentContext);

    // Set the current context based on the page
    setContextFromPage();

    // Make sure the chat toggle is always visible
    const chatToggle = document.querySelector('.chat-toggle');
    if (chatToggle) {
        chatToggle.style.display = 'flex';
    }
}

function generateSessionId() {
    // Generate a random session ID
    return 'session_' + Math.random().toString(36).substring(2, 15);
}

function createChatToggle() {
    const chatToggle = document.createElement('div');
    chatToggle.className = 'chat-toggle';
    chatToggle.innerHTML = '💬';

    document.body.appendChild(chatToggle);

    // Add click event to toggle chat widget visibility
    chatToggle.addEventListener('click', function() {
        const chatWidget = document.getElementById('chat-widget');
        if (chatWidget.style.display === 'flex') {
            chatWidget.style.display = 'none';
        } else {
            chatWidget.style.display = 'flex';
            // Focus on the input field
            const chatInput = document.querySelector('.chat-input');
            if (chatInput) chatInput.focus();
        }
    });
}

function initChatWidgetStructure(chatWidget) {
    // Create chat widget structure
    chatWidget.innerHTML = `
        <div class="chat-header">
            <span>Book Assistant</span>
            <button class="close-btn">×</button>
        </div>
        <div class="chat-body" id="chat-messages">
            <div class="chat-message bot-message">
                Hello! I'm your book assistant. How can I help you today?
            </div>
            <div class="chat-starters" id="chat-starters">
                <!-- Conversation starters will be loaded here -->
            </div>
        </div>
        <div class="chat-footer">
            <input type="text" class="chat-input" placeholder="Type your message...">
            <button class="chat-send-btn">Send</button>
        </div>
    `;

    // Add event listeners
    const closeBtn = chatWidget.querySelector('.close-btn');
    closeBtn.addEventListener('click', function() {
        chatWidget.style.display = 'none';
    });

    const sendBtn = chatWidget.querySelector('.chat-send-btn');
    const chatInput = chatWidget.querySelector('.chat-input');

    sendBtn.addEventListener('click', function() {
        sendMessage(chatInput.value);
    });

    chatInput.addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            sendMessage(chatInput.value);
        }
    });
}

function loadConversationStarters(context) {
    // Fetch conversation starters from the API
    fetch(`/api/chatbot/conversation-starters?context=${context}`)
        .then(response => response.json())
        .then(starters => {
            const startersContainer = document.getElementById('chat-starters');
            if (!startersContainer) return;

            startersContainer.innerHTML = '';

            starters.forEach(starter => {
                const starterBtn = document.createElement('button');
                starterBtn.className = 'starter-btn';
                starterBtn.textContent = starter;
                starterBtn.addEventListener('click', function() {
                    sendMessage(starter);
                });
                startersContainer.appendChild(starterBtn);
            });
        })
        .catch(error => {
            console.error('Error loading conversation starters:', error);
        });
}

function sendMessage(message) {
    if (!message.trim()) return;

    const chatMessages = document.getElementById('chat-messages');
    const chatInput = document.querySelector('.chat-input');
    const chatStarters = document.getElementById('chat-starters');

    // Add user message
    const userMessageDiv = document.createElement('div');
    userMessageDiv.className = 'chat-message user-message';
    userMessageDiv.textContent = message;
    chatMessages.appendChild(userMessageDiv);

    // Clear input and hide starters
    chatInput.value = '';
    if (chatStarters) {
        chatStarters.style.display = 'none';
    }

    // Show loading indicator
    const loadingDiv = document.createElement('div');
    loadingDiv.className = 'chat-message bot-message loading';
    loadingDiv.innerHTML = '<div class="typing-indicator"><span></span><span></span><span></span></div>';
    chatMessages.appendChild(loadingDiv);

    // Scroll to bottom
    chatMessages.scrollTop = chatMessages.scrollHeight;

    // Send message to the API
    fetch('/api/chatbot/chat', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: `sessionId=${sessionId}&message=${encodeURIComponent(message)}&context=${currentContext}`
    })
    .then(response => response.json())
    .then(messages => {
        // Remove loading indicator
        chatMessages.removeChild(loadingDiv);

        // Add bot response
        const botMessage = messages.find(m => m.role === 'bot');
        if (botMessage) {
            const botMessageDiv = document.createElement('div');
            botMessageDiv.className = 'chat-message bot-message';
            botMessageDiv.textContent = botMessage.content;
            chatMessages.appendChild(botMessageDiv);
        }

        // Scroll to bottom
        chatMessages.scrollTop = chatMessages.scrollHeight;
    })
    .catch(error => {
        // Remove loading indicator if it's still in the DOM
        try {
            if (loadingDiv.parentNode === chatMessages) {
                chatMessages.removeChild(loadingDiv);
            }
        } catch (e) {
            console.error('Error removing loading indicator:', e);
        }

        // Add error message
        const errorDiv = document.createElement('div');
        errorDiv.className = 'chat-message bot-message error';
        errorDiv.textContent = 'Sorry, there was an error processing your request. Please try again.';
        chatMessages.appendChild(errorDiv);

        console.error('Error sending message:', error);

        // Scroll to bottom
        chatMessages.scrollTop = chatMessages.scrollHeight;
    });
}

function setContextFromPage() {
    // Set the current context based on the current page
    const path = window.location.pathname;

    if (path === '/') {
        currentContext = 'home';
    } else if (path === '/books') {
        currentContext = 'books';
    } else if (path.startsWith('/books/')) {
        // Extract the book URI from the path
        const bookUri = decodeURIComponent(path.substring(7));
        currentContext = 'book:' + bookUri;
    }

    // Add the selected user to the context if available
    const selectedUser = localStorage.getItem('selectedUser');
    if (selectedUser) {
        currentContext += ';user:' + selectedUser;
    }

    // Load conversation starters for the new context
    loadConversationStarters(currentContext);
}

// Function to search books by theme and author
function searchBooks(theme, author) {
    return fetch(`/api/chatbot/search?theme=${encodeURIComponent(theme || '')}&author=${encodeURIComponent(author || '')}`)
        .then(response => response.json())
        .catch(error => {
            console.error('Error searching books:', error);
            return [];
        });
}
