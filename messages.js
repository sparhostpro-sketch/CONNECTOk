// CONNECTO - Messaging System

async function sendMessage(sender, receiver, content) {
    if (!sender || !receiver) {
        throw new Error("بيانات المستخدم غير مكتملة");
    }

    if (!content || !content.trim()) {
        throw new Error("اكتب رسالة أولاً");
    }

    const message = {
        sender,
        receiver,
        content: content.trim(),
        createdAt: new Date().toISOString(),
        read: false
    };

    return await saveData("messages", message);
}

async function getConversation(user1, user2) {
    const messages = await getAllData("messages");

    return messages
        .filter(message =>
            (message.sender === user1 && message.receiver === user2) ||
            (message.sender === user2 && message.receiver === user1)
        )
        .sort((a, b) =>
            new Date(a.createdAt) - new Date(b.createdAt)
        );
}

async function markMessagesAsRead(receiver, sender) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction("messages", "readwrite");
        const store = transaction.objectStore("messages");
        const request = store.getAll();

        request.onsuccess = function() {
            const messages = request.result;

            messages.forEach(message => {
                if (
                    message.receiver === receiver &&
                    message.sender === sender
                ) {
                    message.read = true;
                    store.put(message);
                }
            });

            resolve(true);
        };

        request.onerror = () => reject(request.error);
    });
}
