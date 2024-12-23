/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.autoconfigure.bedrock.llama;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import software.amazon.awssdk.regions.Region;

import org.springframework.ai.autoconfigure.bedrock.BedrockAwsConnectionProperties;
import org.springframework.ai.autoconfigure.bedrock.BedrockTestUtils;
import org.springframework.ai.autoconfigure.bedrock.RequiresAwsCredentials;
import org.springframework.ai.bedrock.llama.BedrockLlamaChatModel;
import org.springframework.ai.bedrock.llama.api.LlamaChatBedrockApi.LlamaChatModel;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.SystemPromptTemplate;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.within;

/**
 * @author Christian Tzolov
 * @author Wei Jiang
 * @author Mark Pollack
 * @since 1.0.0
 */
@RequiresAwsCredentials
public class BedrockLlamaChatAutoConfigurationIT {

	private final ApplicationContextRunner contextRunner = BedrockTestUtils.getContextRunner()
		.withPropertyValues("spring.ai.bedrock.llama.chat.enabled=true",
				"spring.ai.bedrock.llama.chat.model=" + LlamaChatModel.LLAMA3_70B_INSTRUCT_V1.id(),
				"spring.ai.bedrock.llama.chat.options.temperature=0.5",
				"spring.ai.bedrock.llama.chat.options.maxGenLen=500")
		.withConfiguration(AutoConfigurations.of(BedrockLlamaChatAutoConfiguration.class));

	private final Message systemMessage = new SystemPromptTemplate("""
			You are a helpful AI assistant. Your name is {name}.
			You are an AI assistant that helps people find information.
			Your name is {name}
			You should reply to the user's request with your name and also in the style of a {voice}.
			""").createMessage(Map.of("name", "Bob", "voice", "pirate"));

	private final UserMessage userMessage = new UserMessage(
			"Describe 3 of the most feared and legendary pirates from the Golden Age of Piracy, particularly those known for their intimidating tactics and whose stories influenced popular culture.");

	@Test
	public void chatCompletion() {
		this.contextRunner.run(context -> {
			BedrockLlamaChatModel llamaChatModel = context.getBean(BedrockLlamaChatModel.class);
			ChatResponse response = llamaChatModel.call(new Prompt(List.of(this.userMessage, this.systemMessage)));
			assertThat(response.getResult().getOutput().getText()).contains("Blackbeard");
		});
	}

	@Test
	public void chatCompletionStreaming() {
		this.contextRunner.run(context -> {

			BedrockLlamaChatModel llamaChatModel = context.getBean(BedrockLlamaChatModel.class);

			Flux<ChatResponse> response = llamaChatModel
				.stream(new Prompt(List.of(this.userMessage, this.systemMessage)));

			List<ChatResponse> responses = response.collectList().block();
			assertThat(responses.size()).isGreaterThan(2);

			String stitchedResponseContent = responses.stream()
				.map(ChatResponse::getResults)
				.flatMap(List::stream)
				.map(Generation::getOutput)
				.map(AssistantMessage::getText)
				.collect(Collectors.joining());

			assertThat(stitchedResponseContent).contains("Blackbeard");
		});
	}

	@Test
	public void propertiesTest() {

		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.llama.chat.enabled=true",
					"spring.ai.bedrock.aws.access-key=ACCESS_KEY", "spring.ai.bedrock.aws.secret-key=SECRET_KEY",
					"spring.ai.bedrock.llama.chat.model=MODEL_XYZ",
					"spring.ai.bedrock.aws.region=" + Region.US_EAST_1.id(),
					"spring.ai.bedrock.llama.chat.options.temperature=0.55",
					"spring.ai.bedrock.llama.chat.options.maxGenLen=123")
			.withConfiguration(AutoConfigurations.of(BedrockLlamaChatAutoConfiguration.class))
			.run(context -> {
				var llamaChatProperties = context.getBean(BedrockLlamaChatProperties.class);
				var awsProperties = context.getBean(BedrockAwsConnectionProperties.class);

				assertThat(llamaChatProperties.isEnabled()).isTrue();
				assertThat(awsProperties.getRegion()).isEqualTo(Region.US_EAST_1.id());

				assertThat(llamaChatProperties.getOptions().getTemperature()).isCloseTo(0.55, within(0.0001));
				assertThat(llamaChatProperties.getOptions().getMaxGenLen()).isEqualTo(123);
				assertThat(llamaChatProperties.getModel()).isEqualTo("MODEL_XYZ");

				assertThat(awsProperties.getAccessKey()).isEqualTo("ACCESS_KEY");
				assertThat(awsProperties.getSecretKey()).isEqualTo("SECRET_KEY");
			});
	}

	@Test
	public void chatCompletionDisabled() {

		// It is disabled by default
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withConfiguration(AutoConfigurations.of(BedrockLlamaChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockLlamaChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockLlamaChatModel.class)).isEmpty();
			});

		// Explicitly enable the chat auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.llama.chat.enabled=true")
			.withConfiguration(AutoConfigurations.of(BedrockLlamaChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockLlamaChatProperties.class)).isNotEmpty();
				assertThat(context.getBeansOfType(BedrockLlamaChatModel.class)).isNotEmpty();
			});

		// Explicitly disable the chat auto-configuration.
		BedrockTestUtils.getContextRunnerWithUserConfiguration()
			.withPropertyValues("spring.ai.bedrock.llama.chat.enabled=false")
			.withConfiguration(AutoConfigurations.of(BedrockLlamaChatAutoConfiguration.class))
			.run(context -> {
				assertThat(context.getBeansOfType(BedrockLlamaChatProperties.class)).isEmpty();
				assertThat(context.getBeansOfType(BedrockLlamaChatModel.class)).isEmpty();
			});
	}

}
