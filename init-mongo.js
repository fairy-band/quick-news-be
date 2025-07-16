db = db.getSiblingDB('newsletter');

// Create application user
db.createUser({
  user: 'newsletter',
  pwd: 'newsletter123',
  roles: [
    {
      role: 'readWrite',
      db: 'newsletter'
    }
  ]
});

// Create collections with validation
db.createCollection('newsletter_sources', {
  validator: {
    $jsonSchema: {
      bsonType: 'object',
      required: ['sender', 'senderEmail', 'content', 'receivedDate'],
      properties: {
        subject: {
          bsonType: ['string', 'null'],
          description: 'Newsletter subject'
        },
        sender: {
          bsonType: 'string',
          description: 'Sender name is required'
        },
        senderEmail: {
          bsonType: 'string',
          pattern: '^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$',
          description: 'Valid sender email is required'
        },
        content: {
          bsonType: 'string',
          description: 'Newsletter content is required'
        },
        receivedDate: {
          bsonType: 'date',
          description: 'Received date is required'
        }
      }
    }
  }
});

// Create indexes
db.newsletter_sources.createIndex({ senderEmail: 1 });
db.newsletter_sources.createIndex({ receivedDate: -1 });
db.newsletter_sources.createIndex({ subject: 'text' });
db.newsletter_sources.createIndex({ senderEmail: 1, subject: 1, receivedDate: 1 }, { unique: true });

print('Newsletter database initialized successfully');
