package org.lappsgrid.cloud.stanford;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import groovy.lang.MetaClass;
import org.lappsgrid.api.WebService;
import org.lappsgrid.metadata.ServiceMetadata;
import org.lappsgrid.metadata.ServiceMetadataBuilder;
import org.lappsgrid.rabbitmq.Message;
import org.lappsgrid.rabbitmq.RabbitMQ;
import org.lappsgrid.rabbitmq.topic.PostOffice;
import org.lappsgrid.serialization.Data;
import org.lappsgrid.serialization.Serializer;
import org.lappsgrid.serialization.lif.Container;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.lappsgrid.discriminator.Discriminators.Uri;

/**
 *
 */
public abstract class BaseService implements WebService
{
	static {
		File propFile = new File("/etc/lapps/rabbit-nlp.ini");
		if (!propFile.exists()) {
			propFile = new File("/run/secrets/rabbit-nlp.ini");
		}
		if (propFile.exists()) {
			Properties props = new Properties();
			try
			{
				props.load(new FileReader(propFile));
				System.setProperty("RABBIT_USERNAME", props.getProperty("RABBIT_USERNAME"));
				System.setProperty("RABBIT_PASSWORD", props.getProperty("RABBIT_PASSWORD"));
			}
			catch (IOException ignored)
			{
//				e.printStackTrace();
			}
		}
	}

	static final String HOST = "rabbitmq.lappsgrid.org/nlp";
	static final String POSTOFFICE = "stanford"; //distributed.nlp.stanford";
	static final String STANFORD = "pipelines";

	static final String SEGMENTER = "segmenter";
	static final String POS = "pos";
	static final String LEMMAS = "lemmas";
	static final String NER = "ner";

	private Logger logger = LoggerFactory.getLogger(BaseService.class);

	private String metadata;

	private String username;
	private String password;

	public BaseService()
	{
		username = System.getenv("RABBIT_USERNAME");
		password = System.getenv("RABBIT_PASSWORD");
		logger.info("Username: " + username);
	}

	@Override
	public String execute(final String json)
	{
		logger.info("Received JSON.");
		Data data = Serializer.parse(json);
		String discriminator = data.getDiscriminator();
		if (Uri.ERROR.equals(discriminator)) {
			logger.warn("Data discriminator contained ERROR.");
			return json;
		}
		if (Uri.TEXT.equals(discriminator)) {
			logger.debug("Received TEXT");
			String text = data.getPayload().toString();
			Container container = new Container();
			container.setText(text);
			container.setLanguage("en");
			data.setDiscriminator(Uri.LIF);
			data.setPayload(container);
		}
		else if (!Uri.LIF.equals(discriminator))
		{
			logger.warn("Invalid discriminator type: {}", discriminator);
			data.setDiscriminator(Uri.ERROR);
			data.setPayload("Invalid discriminator type: " + discriminator);
			return data.asPrettyJson();
		}

//		Object semaphore = new Object();
		Signal signal = new Signal();
		String response = null;
		String mbox = UUID.randomUUID().toString();
		PostOffice po = new PostOffice(POSTOFFICE, HOST);
		Box box = null;
		try
		{
			box = new Box(signal, mbox);
		}
		catch (IOException | TimeoutException e)
		{
			logger.error("Unable to create mailbox.", e);
			data.setDiscriminator(Uri.ERROR);
			data.setPayload(e.getMessage());
			return data.asPrettyJson();
		}

		Message message = new Message()
				.body(data.asJson())
				.command(pipeline())
				.route(STANFORD, mbox);

		po.send(message);
		logger.debug("Sent message to {}", message.getRoute().get(0));
		try
		{
			signal.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		try { box.close(); } catch (Exception e) { }
		try { po.close(); } catch (Exception e) { }

		response = box.getResponse();
		if (response == null) {
			response = new Data(Uri.ERROR, "The service did not responde.").asPrettyJson();
		}
		return response;
	}

	@Override
	public String getMetadata()
	{
		if (metadata == null) {
			initMetadata();
		}
		return metadata;
	}

	abstract protected String[] produces();
	abstract protected String pipeline();

	private synchronized void initMetadata()
	{
		if (metadata != null) {
			return;
		}

		ServiceMetadata md = new ServiceMetadataBuilder()
				.name("Distributed Stanford NLP")
				.description("Pipelines from Stanford Core NLP")
				.vendor("http://www.lappsgrid.org")
				.version(Version.getVersion())
				.toolVersion("3.9.2")
				.requireFormats(Uri.TEXT, Uri.LIF)
				.produceFormat(Uri.LIF)
				.license(Uri.GPL3)
				.produces(produces())
				.allow(Uri.ALL)
				.build();

		metadata = new Data(Uri.META, md).asPrettyJson();
	}

	/**
	 * Due to a bug in Groovy we can not extend the org.lappsgrid.rabbitmq.topic.Mailbox
	 * class. See https://issues.apache.org/jira/browse/GROOVY-7362
	 *
	 * The bug is fixed in Groovy 3.0, but as of this writing (June 25, 2019) Groovy 3
	 * has not been released yet.
	 */
	class Box extends RabbitMQ
	{
		private String exchange;
		private String response;
		private Signal signal;

		public Box(Signal signal, String address) throws IOException, TimeoutException {
			super("", HOST);
			this.signal = signal;
//		}
//
//		public Box(String exchange, String address, String host) throws IOException
//		{
			channel.exchangeDeclare(POSTOFFICE, "direct");
			boolean passive = false;
			boolean durable = true;
			boolean exclusive = false;
			boolean autoDelete = true;
			Channel channel = this.channel;
			String qName = channel.queueDeclare("", durable, exclusive, autoDelete, null).getQueue();
			this.queueName = qName;
			channel.queueBind(qName, POSTOFFICE, address);
			channel.basicConsume(qName, false, new MailBoxConsumer(this));
		}

		void recv(String s)
		{
			Message message = Serializer.parse(s, Message.class);
			response = message.getBody().toString();
			signal.send();
		}

		public String getResponse() {
			return this.response;
		}

		class MailBoxConsumer extends DefaultConsumer
		{
			Box box;

			MailBoxConsumer(Box box) {
				super(box.channel);
				this.box = box;
			}

			public void handleDelivery(String consumerTag, Envelope envelope,
								AMQP.BasicProperties properties, byte[] body)
					throws IOException
			{
				String message = new String(body, "UTF-8");
				box.recv(message);
				getChannel().basicAck(envelope.getDeliveryTag(), false);
			}

		}

	}
}
