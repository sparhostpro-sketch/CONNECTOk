
const DB_NAME = "CONNECTO_DB";
const DB_VERSION = 1;

let dbInstance = null;

function openDatabase() {
    return new Promise((resolve, reject) => {
        if (dbInstance) {
            resolve(dbInstance);
            return;
        }

        const request = indexedDB.open(DB_NAME, DB_VERSION);

        request.onupgradeneeded = function(event) {
            const db = event.target.result;

            if (!db.objectStoreNames.contains("users")) {
                db.createObjectStore("users", {
                    keyPath: "id",
                    autoIncrement: true
                });
            }

            if (!db.objectStoreNames.contains("posts")) {
                db.createObjectStore("posts", {
                    keyPath: "id",
                    autoIncrement: true
                });
            }

            if (!db.objectStoreNames.contains("messages")) {
                db.createObjectStore("messages", {
                    keyPath: "id",
                    autoIncrement: true
                });
            }
        };

        request.onsuccess = function(event) {
            dbInstance = event.target.result;
            resolve(dbInstance);
        };

        request.onerror = function() {
            reject(request.error);
        };
    });
}

async function saveData(storeName, data) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, "readwrite");
        const store = transaction.objectStore(storeName);
        const request = store.add(data);

        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}

async function getAllData(storeName) {
    const db = await openDatabase();

    return new Promise((resolve, reject) => {
        const transaction = db.transaction(storeName, "readonly");
        const request = transaction.objectStore(storeName).getAll();

        request.onsuccess = () => resolve(request.result);
        request.onerror = () => reject(request.error);
    });
}
