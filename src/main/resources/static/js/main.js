document.addEventListener('DOMContentLoaded', function() {
    // Initialize chat widget
    initChatWidget();

    // Initialize user selection
    initUserSelection();
});

function initChatWidget() {
    // This function will be implemented later when we work on the chatbot feature
    console.log('Chat widget will be implemented in a future step');

    // For now, we'll just add a placeholder toggle button
    const chatToggle = document.createElement('div');
    chatToggle.className = 'chat-toggle';
    chatToggle.innerHTML = '<i class="fas fa-comments"></i>';
    chatToggle.textContent = '💬';

    document.body.appendChild(chatToggle);

    // Add click event to toggle chat widget visibility
    chatToggle.addEventListener('click', function() {
        const chatWidget = document.getElementById('chat-widget');
        if (chatWidget.style.display === 'flex') {
            chatWidget.style.display = 'none';
        } else {
            // Initialize chat widget structure when shown for the first time
            if (!chatWidget.querySelector('.chat-header')) {
                initChatWidgetStructure(chatWidget);
            }
            chatWidget.style.display = 'flex';
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
            <div class="chat-starters">
                <button class="starter-btn">What books do you recommend?</button>
                <button class="starter-btn">Find books by genre</button>
                <button class="starter-btn">Help me find a specific book</button>
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

    const starterBtns = chatWidget.querySelectorAll('.starter-btn');
    starterBtns.forEach(btn => {
        btn.addEventListener('click', function() {
            const chatInput = chatWidget.querySelector('.chat-input');
            chatInput.value = this.textContent;
            // In a future implementation, this would trigger the chatbot response
        });
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

function sendMessage(message) {
    if (!message.trim()) return;

    const chatMessages = document.getElementById('chat-messages');
    const chatInput = document.querySelector('.chat-input');

    // Add user message
    const userMessageDiv = document.createElement('div');
    userMessageDiv.className = 'chat-message user-message';
    userMessageDiv.textContent = message;
    chatMessages.appendChild(userMessageDiv);

    // Clear input
    chatInput.value = '';

    // Scroll to bottom
    chatMessages.scrollTop = chatMessages.scrollHeight;

    // In a future implementation, this would trigger the chatbot response
    // For now, just add a placeholder response
    setTimeout(() => {
        const botMessageDiv = document.createElement('div');
        botMessageDiv.className = 'chat-message bot-message';
        botMessageDiv.textContent = "I'm still learning. This feature will be implemented soon!";
        chatMessages.appendChild(botMessageDiv);
        chatMessages.scrollTop = chatMessages.scrollHeight;
    }, 1000);
}

function initUserSelection() {
    // This function is now empty as we're using server-side rendering with Thymeleaf
    // to display the selected user. The button text is updated by Thymeleaf based on
    // the currentUser attribute in the model.
    console.log('User selection is now handled by server-side rendering');
}

// This function is no longer used as we're using form submissions for user selection
function selectUser(username) {
    console.log('This function is deprecated. User selection is now handled by form submissions.');
}

function updateUserDropdown(username) {
    const userDropdown = document.getElementById('userDropdown');
    if (!userDropdown) return;

    // Update the dropdown button text (find the span inside the button)
    const span = userDropdown.querySelector('span');
    if (span) {
        span.textContent = username;
    } else {
        // Fallback if span doesn't exist
        userDropdown.textContent = username;
    }

    // Add a visual indicator that a user is selected
    userDropdown.classList.add('btn-primary');
    userDropdown.classList.remove('btn-outline-primary');
}
