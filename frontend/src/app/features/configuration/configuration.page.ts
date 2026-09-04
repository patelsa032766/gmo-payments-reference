import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActiveConfiguration, BrowserPaymentConfiguration, CheckoutApiService, ConfiguredMethod } from '../../core/api/checkout-api.service';

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
  protected readonly configuration = signal<ActiveConfiguration | null>(null);
  protected readonly failed = signal(false);
  protected readonly draftVersion = signal<number | null>(null);
  protected readonly methods = signal<ConfiguredMethod[]>([]);
  protected readonly dirty = signal(false);
  protected readonly saving = signal(false);
  protected readonly message = signal<string | null>(null);
  protected readonly integration = signal<BrowserPaymentConfiguration | null>(null);
  protected operatorToken = '';
  protected language: 'en'|'ja' = 'en';
  protected channel = 'PA';
  protected amount = 10000;
  protected monthly = true;
  protected ekyc = true;

  ngOnInit(): void {
    this.api.getConfigurationWorkspace().subscribe({
      next: workspace => { this.configuration.set(workspace.active); this.methods.set(structuredClone(workspace.draft?.methods ?? workspace.active.methods)); this.draftVersion.set(workspace.draft?.version ?? null); this.dirty.set(!!workspace.draft); },
      error: () => this.failed.set(true),
    });
    this.api.getBrowserConfiguration().subscribe(configuration => this.integration.set(configuration));
  }

  protected changed(): void { this.dirty.set(true); this.message.set(null); this.methods.set([...this.methods()]); }
  protected toggle(method: ConfiguredMethod): void { method.enabled=!method.enabled; this.changed(); }
  protected move(index:number,direction:-1|1):void { const next=index+direction; const methods=[...this.methods()]; if(next<0||next>=methods.length)return; [methods[index],methods[next]]=[methods[next],methods[index]]; methods.forEach((method,i)=>method.displayOrder=i+1); this.methods.set(methods); this.changed(); }
  protected publish():void {
    if(!this.operatorToken){this.message.set('Enter the operator token to publish changes.');return;}
    this.saving.set(true); this.api.saveConfigurationDraft(this.methods(),this.operatorToken).subscribe({next:draft=>{this.draftVersion.set(draft.version);this.api.publishConfiguration(this.operatorToken).subscribe({next:active=>{this.configuration.set(active);this.methods.set(structuredClone(active.methods));this.draftVersion.set(null);this.dirty.set(false);this.saving.set(false);this.message.set(`Version ${active.version} is now serving Checkout.`);},error:()=>this.failure('The draft was saved but could not be published.')});},error:()=>this.failure('The draft could not be saved. Check the operator token and values.')});
  }
  protected discard():void {
    if(!this.configuration())return; if(!this.draftVersion()){this.methods.set(structuredClone(this.configuration()!.methods));this.dirty.set(false);return;}
    if(!this.operatorToken){this.message.set('Enter the operator token to discard the saved draft.');return;}
    this.api.discardConfigurationDraft(this.operatorToken).subscribe({next:()=>{this.methods.set(structuredClone(this.configuration()!.methods));this.draftVersion.set(null);this.dirty.set(false);this.message.set('Draft discarded.');},error:()=>this.failure('The draft could not be discarded.')});
  }
  protected name(code:string):string { return ({card:'Credit or debit card',paypay:'PayPay',bankDirect:'Real-time bank debit',kozaFurikae:'Bank transfer today + monthly bank debit',kombini:'Convenience store',payeasy:'Pay-easy',furikomi:'Bank transfer'} as Record<string,string>)[code]??code; }
  protected supportsAuthorization(code:string):boolean{return code==='card'||code==='paypay';}
  private failure(message:string):void{this.saving.set(false);this.message.set(message);}
}
