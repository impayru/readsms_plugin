class SMS {
  /// The body of the message received
  final String body;

  /// Senders contact
  final String sender;

  /// The time when sms is received
  final DateTime timeReceived;

  final int simId;

  SMS({required this.body, required this.sender, required this.timeReceived, required this.simId});

  /// creates an SMS instance from the list of objects
  /// received from the broadcast stream of event channel
  SMS.fromList(List data)
      : body = data[0] as String,
        sender = data[1] as String,
        // convert the time received in milliseconds to DateTime object
        timeReceived = DateTime.fromMillisecondsSinceEpoch(
          int.parse(data[2] as String),
        ),
        simId = data[4] as int;
}
