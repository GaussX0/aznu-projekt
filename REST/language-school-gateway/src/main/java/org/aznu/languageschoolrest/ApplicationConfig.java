package org.aznu.languageschoolrest;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

@Configuration
public class ApplicationConfig {

    @Bean
    public StateMachineBuilder stateMachineBuilder() {
        return new StateMachineBuilder()
                .initialState(ProcessingState.NONE)
                .add(ProcessingState.NONE, ProcessingEvent.START, ProcessingState.STARTED)
                .add(ProcessingState.STARTED, ProcessingEvent.FINISH, ProcessingState.FINISHED)
                .add(ProcessingState.FINISHED, ProcessingEvent.COMPLETE, ProcessingState.COMPLETED)
                .add(ProcessingState.NONE, ProcessingEvent.CANCEL, ProcessingState.CANCELLED)
                .add(ProcessingState.STARTED, ProcessingEvent.CANCEL, ProcessingState.CANCELLED)
                .add(ProcessingState.FINISHED, ProcessingEvent.CANCEL, ProcessingState.CANCELLED)
                .add(ProcessingState.CANCELLED, ProcessingEvent.CANCEL, ProcessingState.CANCELLED);
    }

    @Bean
    @Scope("prototype")
    public StateService stateService(StateMachineBuilder stateMachineBuilder) {
        return new StateService(stateMachineBuilder);
    }
}