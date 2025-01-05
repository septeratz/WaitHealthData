

/**
 * @type {import('@types/aws-lambda').APIGatewayProxyHandler}
 */
const AWS = require('aws-sdk');
const dynamoDB = new AWS.DynamoDB.DocumentClient();

exports.handler = async (event) => {
    const data = JSON.parse(event.body);
    const params = {
        TableName: "RestHeartrate",
        Item: {
            id: Date.now().toString(),
            heartRate: data.heartRate,
            timestamp: data.timestamp
        }
    };

    try {
        await dynamoDB.put(params).promise();
        return {
            statusCode: 200,
            body: JSON.stringify({ message: 'Data saved successfully!' })
        };
    } catch (error) {
        console.error(error);
        return {
            statusCode: 500,
            body: JSON.stringify({ message: 'Failed to save data.' })
        };
    }
};
