import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActiveConfiguration, BrowserPaymentConfiguration, CheckoutApiService, CheckoutExperienceSettings, CheckoutScenario, ConfiguredMethod } from '../../core/api/checkout-api.service';
import { OperatorCredentialService } from '../../core/auth/operator-credential.service';
import { switchMap } from 'rxjs';

/** Protected copy-on-write checkout configuration administrator. */
@Component({
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  selector: 'app-configuration-page',
  styleUrl: './configuration.page.scss',
  templateUrl: './configuration.page.html',
})
export class ConfigurationPage implements OnInit {
  private readonly api = inject(CheckoutApiService);
  private readonly operatorCredential = inject(OperatorCredentialService);
  protected readonly configuration = signal<ActiveConfiguration | null>(null);
  protected readonly failed = signal(false);
  protected readonly draftVersion = signal<number | null>(null);
  protected readonly methods = signal<ConfiguredMethod[]>([]);
  protected readonly dirty = signal(false);
  protected readonly saving = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly integration = signal<BrowserPaymentConfiguration | null>(null);
  protected readonly experience = signal<CheckoutExperienceSettings | null>(null);
  protected readonly persistedTokenRequired = signal(true);
  protected operatorToken = '';
  protected language: 'en'|'ja' = 'en';
  protected selectedApplication = '';
  protected amount = 10000;
  protected configurationTokenRequired = true;

  ngOnInit(): void {
    this.operatorToken = this.operatorCredential.current();
    this.api.getConfigurationWorkspace().subscribe({
      next: workspace => { this.configuration.set(workspace.active); this.methods.set(structuredClone(workspace.draft?.methods ?? workspace.active.methods)); this.draftVersion.set(workspace.draft?.version ?? null); this.dirty.set(!!workspace.draft); },
      error: () => this.failed.set(true),
    });
    this.api.getBrowserConfiguration().subscribe(configuration => this.integration.set(configuration));
    this.api.getCheckoutExperience().subscribe(settings => {
      this.experience.set(settings);
      this.selectedApplication=settings.selectedApplicationNumber;
      this.configurationTokenRequired=settings.configurationTokenRequired;
      this.language=settings.checkoutLanguage;
      this.persistedTokenRequired.set(settings.configurationTokenRequired);
      this.amount=settings.customers.find(item=>item.applicationNumber===settings.selectedApplicationNumber)?.amountJpy??10000;
    });
  }

  protected changed(): void { this.dirty.set(true); this.message.set(null); this.methods.set([...this.methods()]); }
  protected rememberOperatorToken(value: string): void {
    this.operatorToken = value;
    this.operatorCredential.rememberForCurrentTab(value);
  }
  protected toggle(method: ConfiguredMethod): void { method.enabled=!method.enabled; this.changed(); }
  protected selectedCustomer():CheckoutScenario|null{return this.experience()?.customers.find(item=>item.applicationNumber===this.selectedApplication)??null;}
  protected customerChanged():void{const customer=this.selectedCustomer();if(customer)this.amount=customer.amountJpy;this.changed();}
  protected move(index:number,direction:-1|1):void { const next=index+direction; const methods=[...this.methods()]; if(next<0||next>=methods.length)return; [methods[index],methods[next]]=[methods[next],methods[index]]; methods.forEach((method,i)=>method.displayOrder=i+1); this.methods.set(methods); this.changed(); }
  protected publish():void {
    if(this.persistedTokenRequired()&&!this.operatorToken){this.message.set('Enter the operator token to save changes.');return;}
    if(!this.selectedCustomer()||!Number.isFinite(this.amount)||this.amount<1){this.message.set('Choose a customer and enter a valid amount of at least JPY 1.');return;}
    this.saving.set(true);
    this.api.saveConfigurationDraft(this.methods(),this.operatorToken).pipe(
      switchMap(draft=>{this.draftVersion.set(draft.version);return this.api.publishConfiguration(this.operatorToken);}),
      switchMap(active=>{this.configuration.set(active);return this.api.saveCheckoutExperience(this.selectedApplication,this.amount,this.configurationTokenRequired,this.language,this.operatorToken);})
    ).subscribe({next:settings=>{this.experience.set(settings);this.persistedTokenRequired.set(settings.configurationTokenRequired);this.methods.set(structuredClone(this.configuration()!.methods));this.draftVersion.set(null);this.dirty.set(false);this.saving.set(false);this.message.set(`Saved. Version ${this.configuration()!.version} and the checkout scenario are now active.`);},error:()=>this.failure('Changes could not be saved. Check the operator token and values.')});
  }
  protected discard():void {
    if(!this.configuration())return; if(!this.draftVersion()){this.restorePersistedValues();this.dirty.set(false);return;}
    if(this.persistedTokenRequired()&&!this.operatorToken){this.message.set('Enter the operator token to discard the saved draft.');return;}
    this.api.discardConfigurationDraft(this.operatorToken).subscribe({next:()=>{this.restorePersistedValues();this.draftVersion.set(null);this.dirty.set(false);this.message.set('Draft discarded.');},error:()=>this.failure('The draft could not be discarded.')});
  }
  protected name(code:string):string { return ({card:'Credit or debit card',paypay:'PayPay',bankDirect:'Real-time bank debit',kozaFurikae:'Bank transfer today + monthly bank debit',kombini:'Convenience store',payeasy:'Pay-easy',furikomi:'Bank transfer'} as Record<string,string>)[code]??code; }
  protected supportsAuthorization(code:string):boolean{return code==='card'||code==='paypay';}
  protected tokenNeeded():boolean{return this.persistedTokenRequired();}
  private restorePersistedValues():void{
    this.methods.set(structuredClone(this.configuration()!.methods));
    const settings=this.experience();
    if(!settings)return;
    this.selectedApplication=settings.selectedApplicationNumber;
    this.configurationTokenRequired=settings.configurationTokenRequired;
    this.language=settings.checkoutLanguage;
    this.amount=settings.customers.find(item=>item.applicationNumber===settings.selectedApplicationNumber)?.amountJpy??10000;
  }
  private failure(message:string):void{this.saving.set(false);this.message.set(message);}
}
