package io.pragmatic.ddd.scenario.application.person;

import io.pragmatic.ddd.application.AbstractApplicationService;
import io.pragmatic.ddd.application.ICommandApplicationService;
import io.pragmatic.ddd.application.ICommandExecutor;
import io.pragmatic.ddd.application.outbox.EagerOutboxPublisher;

import java.util.concurrent.Executors;
import io.pragmatic.ddd.application.outbox.OutboxCommandExecutor;
import io.pragmatic.ddd.application.outbox.OutboxRelay;
import io.pragmatic.ddd.application.outbox.OutboxRelayConfig;
import io.pragmatic.ddd.application.outbox.fixture.InMemoryOutboxStore;
import io.pragmatic.ddd.application.outbox.fixture.StubEventSerializer;
import io.pragmatic.ddd.application.outbox.fixture.SyncTransactionOperations;
import io.pragmatic.ddd.application.outbox.spi.IOutboxStore;
import io.pragmatic.ddd.application.outbox.spi.TransactionOperations;
import io.pragmatic.ddd.event.local.ThreadPoolEventManager;
import io.pragmatic.ddd.event.spi.IEventManager;
import io.pragmatic.ddd.event.spi.IEventSerializer;
import io.pragmatic.ddd.scenario.application.person.factory.PersonFactory;
import io.pragmatic.ddd.scenario.application.person.input.ArchivePersonInput;
import io.pragmatic.ddd.scenario.application.person.input.AssignDeptInput;
import io.pragmatic.ddd.scenario.application.person.input.BindEmailInput;
import io.pragmatic.ddd.scenario.application.person.input.BindPhoneInput;
import io.pragmatic.ddd.scenario.application.person.input.ChangeStatusInput;
import io.pragmatic.ddd.scenario.application.person.input.CreatePersonInput;
import io.pragmatic.ddd.scenario.application.person.input.FreezePersonInput;
import io.pragmatic.ddd.scenario.application.person.input.TagPersonInput;
import io.pragmatic.ddd.scenario.application.person.input.UnfreezePersonInput;
import io.pragmatic.ddd.scenario.application.person.input.UpdatePersonInput;
import io.pragmatic.ddd.scenario.application.person.input.UpdatePersonStatusInput;
import io.pragmatic.ddd.scenario.application.person.rule.PersonRuleAssembler;
import io.pragmatic.ddd.scenario.application.person.updater.PersonUpdater;
import io.pragmatic.ddd.scenario.domain.person.model.Person;
import io.pragmatic.ddd.scenario.domain.person.repository.IPersonRepository;
import io.pragmatic.ddd.scenario.domain.person.rule.PersonEntityRule;

/**
 * 人员命令应用服务。
 *
 * @author wizard-lee
 */
public class PersonWriteService extends AbstractApplicationService implements ICommandApplicationService {

    private final IPersonRepository repository;
    private final PersonFactory factory;
    private final PersonUpdater updater;
    private final PersonEntityRule entityRule;

    public PersonWriteService(IPersonRepository repository) {
        this(repository,
                new ThreadPoolEventManager(),
                new PersonFactory(),
                new PersonUpdater(),
                new PersonRuleAssembler().build());
    }

    public PersonWriteService(IPersonRepository repository,
                              IEventManager eventManager,
                              PersonFactory factory,
                              PersonUpdater updater,
                              PersonEntityRule entityRule) {
        super(eventManager, buildOutboxExecutor(eventManager));
        this.repository = repository;
        this.factory = factory;
        this.updater = updater;
        this.entityRule = entityRule;
    }

    private static ICommandExecutor buildOutboxExecutor(IEventManager eventManager) {
        IOutboxStore outboxStore = new InMemoryOutboxStore();
        TransactionOperations transactionOperations = new SyncTransactionOperations();
        IEventSerializer eventSerializer = new StubEventSerializer();
        EagerOutboxPublisher eagerPublisher = new EagerOutboxPublisher(
                outboxStore, eventManager, Executors.newFixedThreadPool(4));
        new OutboxRelay(outboxStore, eventManager, eventSerializer,
                Executors.newSingleThreadScheduledExecutor(), OutboxRelayConfig.defaultConfig());
        return new OutboxCommandExecutor(
                outboxStore, transactionOperations, eventSerializer, eagerPublisher);
    }

    public long createPerson(CreatePersonInput input) {
        Person person = factory.create(input);
        Person saved = this.execute(person, entityRule, repository, p -> { });
        return saved.getEntityId();
    }

    public void updatePerson(UpdatePersonInput input) {
        Person person = repository.findById(input.getId());
        if (person != null) {
            this.execute(person, entityRule, repository, p -> updater.apply(p, input));
        }
    }

    public void updateStatus(UpdatePersonStatusInput input) {
        Person person = repository.findById(input.getId());
        if (person != null) {
            this.execute(person, entityRule, repository, p -> p.updateStatus(input.getStatus()));
        }
    }

    public void freezePerson(FreezePersonInput input) {
        Person person = repository.findById(input.getId());
        if (person != null) {
            this.execute(person, entityRule, repository, p -> p.freeze(input.getReason()));
        }
    }

    public void unfreezePerson(UnfreezePersonInput input) {
        Person person = repository.findById(input.getId());
        if (person != null) {
            this.execute(person, entityRule, repository, Person::unfreeze);
        }
    }

    public void bindEmail(BindEmailInput input) {
        Person person = repository.findById(input.getId());
        if (person != null) {
            this.execute(person, entityRule, repository, p -> p.bindEmail(input.getEmail()));
        }
    }

    public void bindPhone(BindPhoneInput input) {
        Person person = repository.findById(input.getId());
        if (person != null) {
            this.execute(person, entityRule, repository, p -> p.bindPhone(input.getPhone()));
        }
    }

    public void assignDepartment(AssignDeptInput input) {
        Person person = repository.findById(input.getId());
        if (person != null) {
            this.execute(person, entityRule, repository,
                    p -> p.assignDepartment(input.getDepartmentId(), input.getPosition()));
        }
    }

    public void tagPerson(TagPersonInput input) {
        Person person = repository.findById(input.getId());
        if (person != null) {
            this.execute(person, entityRule, repository, p -> p.tag(input.getTags()));
        }
    }

    public void archivePerson(ArchivePersonInput input) {
        Person person = repository.findById(input.getId());
        if (person != null) {
            this.execute(person, entityRule, repository, Person::archive);
        }
    }

    public void changeStatus(ChangeStatusInput input) {
        Person person = repository.findById(input.getId());
        if (person != null) {
            this.execute(person, entityRule, repository,
                    p -> p.changeStatus(input.getStatus(), input.getReason()));
        }
    }
}
